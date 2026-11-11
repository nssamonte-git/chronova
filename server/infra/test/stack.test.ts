import { App } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import { ChronovaStack } from '../lib/chronova-stack';

const synth = (stage: string, voice?: boolean) =>
  Template.fromStack(new ChronovaStack(new App(), `Test-${stage}`, { stage, mailFrom: 'no-reply@example.com', voice }));
const containersOf = (template: Template) =>
  Object.values(template.findResources('AWS::ECS::TaskDefinition')).flatMap(
    (r) => r.Properties.ContainerDefinitions as Array<Record<string, any>>,
  );
const envOf = (container: Record<string, any>) =>
  Object.fromEntries((container.Environment as Array<{ Name: string; Value: string }>).map((e) => [e.Name, e.Value]));
const secretNamesOf = (container: Record<string, any>) => (container.Secrets as Array<{ Name: string }>).map((s) => s.Name);

const dev = synth('dev');
const CACHING_DISABLED = '4135ea2d-6df8-44a3-9df3-4b5a84be39ad';

describe('network', () => {
  it('has no NAT gateway', () => {
    dev.resourceCountIs('AWS::EC2::NatGateway', 0);
  });

  it('keeps the load balancer internal, reachable only from inside the VPC', () => {
    dev.hasResourceProperties('AWS::ElasticLoadBalancingV2::LoadBalancer', { Scheme: 'internal' });
  });

  it('lets only the load balancer reach the web tasks, and only the tasks reach Postgres', () => {
    const ingress = Object.values(dev.findResources('AWS::EC2::SecurityGroupIngress')).map((r) => r.Properties);
    expect(ingress.map((p) => p.FromPort).sort()).toEqual([5432, 8080]);
    expect(ingress.every((p) => p.SourceSecurityGroupId !== undefined && p.CidrIp === undefined)).toBe(true);
  });
});

describe('database', () => {
  it('is private, encrypted and requires SSL', () => {
    dev.hasResourceProperties('AWS::RDS::DBInstance', {
      PubliclyAccessible: false,
      StorageEncrypted: true,
      Engine: 'postgres',
    });
    dev.hasResourceProperties('AWS::RDS::DBParameterGroup', { Parameters: { 'rds.force_ssl': '1' } });
  });

  it('is protected and highly available in prod only', () => {
    dev.hasResourceProperties('AWS::RDS::DBInstance', { MultiAZ: false, DeletionProtection: false });
    const prod = synth('prod');
    prod.hasResourceProperties('AWS::RDS::DBInstance', { MultiAZ: true, DeletionProtection: true });
    prod.hasResource('AWS::RDS::DBInstance', { DeletionPolicy: 'Retain' });
  });
});

describe('cloudfront', () => {
  it('serves HTTPS only, through a VPC origin, without caching app responses', () => {
    dev.hasResourceProperties('AWS::CloudFront::Distribution', {
      DistributionConfig: Match.objectLike({
        DefaultCacheBehavior: Match.objectLike({ ViewerProtocolPolicy: 'redirect-to-https', CachePolicyId: CACHING_DISABLED }),
        Origins: [Match.objectLike({ VpcOriginConfig: Match.anyValue() })],
      }),
    });
  });

  it('forwards viewer headers (incl. Authorization) and the CloudFront client address', () => {
    dev.hasResourceProperties('AWS::CloudFront::OriginRequestPolicy', {
      OriginRequestPolicyConfig: Match.objectLike({
        HeadersConfig: { HeaderBehavior: 'allViewerAndWhitelistCloudFront', Headers: ['CloudFront-Viewer-Address'] },
      }),
    });
  });
});

describe('containers', () => {
  const containers = Object.values(dev.findResources('AWS::ECS::TaskDefinition')).flatMap(
    (r) => r.Properties.ContainerDefinitions as Array<Record<string, any>>,
  );

  it('runs web, worker, scheduler and migrate roles of one image on ARM64', () => {
    expect(containers.map((c) => c.Command[0]).sort()).toEqual(['migrate', 'scheduler', 'web', 'worker']);
    dev.allResourcesProperties('AWS::ECS::TaskDefinition', { RuntimePlatform: { CpuArchitecture: 'ARM64', OperatingSystemFamily: 'LINUX' } });
  });

  it('injects secrets from Secrets Manager, never as plain environment values', () => {
    for (const container of containers) {
      const env = (container.Environment as Array<{ Name: string }>).map((e) => e.Name);
      const secrets = (container.Secrets as Array<{ Name: string }>).map((s) => s.Name);
      expect(env).not.toEqual(expect.arrayContaining(['APP_KEY']));
      expect(env).not.toEqual(expect.arrayContaining(['DB_PASSWORD']));
      expect(secrets).toEqual(expect.arrayContaining(['APP_KEY', 'DB_PASSWORD', 'DB_HOST']));
      expect(container.Environment).toEqual(expect.arrayContaining([{ Name: 'BEHIND_CLOUDFRONT', Value: 'true' }]));
    }
  });

  it('health-checks the web container on /up', () => {
    const web = containers.find((c) => c.Command[0] === 'web')!;
    expect(web.HealthCheck.Command).toEqual(['CMD', 'curl', '-fsS', 'http://127.0.0.1:8080/up']);
    dev.hasResourceProperties('AWS::ElasticLoadBalancingV2::TargetGroup', { HealthCheckPath: '/up', Port: 8080 });
  });

  it('allows sending mail only from the configured SES identity', () => {
    const statements = Object.values(dev.findResources('AWS::IAM::Policy'))
      .flatMap((r) => r.Properties.PolicyDocument.Statement as Array<Record<string, any>>)
      .filter((s) => [s.Action].flat().some((a: string) => a.startsWith('ses:')));
    expect(statements.length).toBeGreaterThan(0);
    for (const statement of statements) {
      expect(JSON.stringify(statement.Resource)).toContain('identity/no-reply@example.com');
      expect(statement.Resource).not.toBe('*');
    }
  });

  it('keeps container logs for a month', () => {
    dev.allResourcesProperties('AWS::Logs::LogGroup', { RetentionInDays: 30 });
  });

  it('allows interactive admin commands on the jobs service only', () => {
    const services = Object.values(dev.findResources('AWS::ECS::Service')).map((r) => r.Properties.EnableExecuteCommand ?? false);
    expect(services.sort()).toEqual([false, true]);
  });
});

describe('voice', () => {
  it('keeps a secret for the provider keys, created without real values', () => {
    dev.hasResourceProperties('AWS::SecretsManager::Secret', {
      GenerateSecretString: Match.objectLike({ SecretStringTemplate: '{"openai":"","elevenlabs":""}', GenerateStringKey: 'placeholder' }),
    });
    dev.hasOutput('VoiceKeysSecret', Match.anyValue());
  });

  it('is off by default: no provider keys in any container, fake drivers', () => {
    for (const container of containersOf(dev)) {
      expect(envOf(container)).toMatchObject({ VOICE_ENABLED: 'false', VOICE_INTERPRETER: 'fake', VOICE_SPEECH: 'fake' });
      expect(secretNamesOf(container)).not.toEqual(expect.arrayContaining(['OPENAI_API_KEY']));
      expect(secretNamesOf(container)).not.toEqual(expect.arrayContaining(['ELEVENLABS_API_KEY']));
    }
  });

  it('when on, gives the provider keys to the web container only, as secrets', () => {
    const on = synth('dev', true);
    for (const container of containersOf(on)) {
      const env = envOf(container);
      expect(env).toMatchObject({
        VOICE_ENABLED: 'true',
        VOICE_TRANSCRIBER: 'elevenlabs',
        VOICE_INTERPRETER: 'openai',
        VOICE_EXPLAINER: 'openai',
        VOICE_SPEECH: 'elevenlabs',
      });
      expect(Object.keys(env)).not.toEqual(expect.arrayContaining(['OPENAI_API_KEY', 'ELEVENLABS_API_KEY']));
      const hasKeys = secretNamesOf(container).includes('OPENAI_API_KEY') && secretNamesOf(container).includes('ELEVENLABS_API_KEY');
      expect(hasKeys).toBe(container.Command[0] === 'web');
    }
    const web = containersOf(on).find((c) => c.Command[0] === 'web')!;
    const openai = (web.Secrets as Array<{ Name: string; ValueFrom: unknown }>).find((s) => s.Name === 'OPENAI_API_KEY')!;
    expect(JSON.stringify(openai.ValueFrom)).toContain(':openai::');
  });
});
