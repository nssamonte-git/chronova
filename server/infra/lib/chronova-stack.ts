import * as path from 'node:path';
import { CfnOutput, Duration, RemovalPolicy, Stack, type StackProps } from 'aws-cdk-lib';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cwActions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecrAssets from 'aws-cdk-lib/aws-ecr-assets';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as ses from 'aws-cdk-lib/aws-ses';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as subs from 'aws-cdk-lib/aws-sns-subscriptions';
import type { Construct } from 'constructs';

export interface ChronovaStackProps extends StackProps {
  /** "dev" or "prod". Prod keeps the database on stack deletion, runs it Multi-AZ and turns on deletion protection. */
  readonly stage: string;
  /** Address the app sends verification emails from; SES emails it a verification link on first deploy. */
  readonly mailFrom: string;
  /**
   * Verify the whole domain of `mailFrom` with DKIM (three CNAME records, printed as outputs) instead of
   * the single address. Needed when nobody reads that mailbox (no-reply@…). Off: SES emails the address
   * a verification link.
   */
  readonly mailDomain?: boolean;
  /** Optional: where alarm notifications go. */
  readonly alarmEmail?: string;
  /** Build context of the server image (the Laravel app). Defaults to ../ (server/). */
  readonly imageDirectory?: string;
  /**
   * Voice editing with the real providers (OpenAI, ElevenLabs). Off by default: put the keys in the
   * VoiceKeys secret first (DEPLOY.md). Deploying with it off is also the kill switch.
   */
  readonly voice?: boolean;
  /**
   * Push notifications through Firebase Cloud Messaging. Off by default: put the service-account JSON
   * in the PushKey secret first (DEPLOY.md). Off, the server records pushes and sends none.
   */
  readonly push?: boolean;
  /**
   * Schedules from images (docs/import-api.md) with the real providers: Meta reads the image, OpenAI
   * builds the schedule. Off by default: put the Meta key (and the OpenAI key) in the VoiceKeys secret first.
   */
  readonly import?: boolean;
}

/**
 * The Chronova server on AWS:
 *
 *   phone / browser ──HTTPS──▶ CloudFront ──VPC origin (private)──▶ internal ALB ──▶ ECS Fargate "web"
 *                                                                   ECS Fargate "jobs" (queue worker + scheduler)
 *                                                                   RDS Postgres (isolated subnets)
 *
 * - No custom domain needed: CloudFront serves https://<id>.cloudfront.net.
 * - The load balancer is internal; only CloudFront reaches it, so nobody can bypass
 *   CloudFront, and the app can trust CloudFront-Viewer-Address for client IPs.
 * - No NAT gateway: tasks sit in public subnets (to pull images and call AWS APIs)
 *   but accept traffic only from the load balancer's security group.
 * - Secrets (app key, database password, voice provider keys, the Firebase service account) live
 *   in Secrets Manager and are injected at start; email goes through SES with the task role (no
 *   SMTP credentials). Only the web container gets the voice provider keys; the web, worker and
 *   scheduler containers get the Firebase key when push is on (each of them sends pushes).
 */
export class ChronovaStack extends Stack {
  constructor(scope: Construct, id: string, props: ChronovaStackProps) {
    super(scope, id, props);
    const isProd = props.stage === 'prod';

    const vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [
        { name: 'public', subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 },
        { name: 'private', subnetType: ec2.SubnetType.PRIVATE_ISOLATED, cidrMask: 24 },
      ],
    });

    // --- Database -----------------------------------------------------------------
    const dbSecurityGroup = new ec2.SecurityGroup(this, 'DatabaseSg', { vpc, description: 'Postgres: app tasks only', allowAllOutbound: false });
    const database = new rds.DatabaseInstance(this, 'Database', {
      engine: rds.DatabaseInstanceEngine.postgres({ version: rds.PostgresEngineVersion.VER_16 }),
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T4G, ec2.InstanceSize.MICRO),
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      securityGroups: [dbSecurityGroup],
      publiclyAccessible: false,
      databaseName: 'chronova',
      credentials: rds.Credentials.fromGeneratedSecret('chronova'),
      storageEncrypted: true,
      allocatedStorage: 20,
      maxAllocatedStorage: 100,
      storageType: rds.StorageType.GP3,
      multiAz: isProd,
      backupRetention: Duration.days(isProd ? 14 : 3),
      deletionProtection: isProd,
      removalPolicy: isProd ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY,
      cloudwatchLogsExports: ['postgresql'],
      cloudwatchLogsRetention: logs.RetentionDays.ONE_MONTH,
      parameters: { 'rds.force_ssl': '1' },
    });
    const dbSecret = database.secret!;

    // Laravel's APP_KEY: 32 random characters (a raw AES-256 key).
    const appKey = new secretsmanager.Secret(this, 'AppKey', {
      description: 'Chronova Laravel APP_KEY',
      generateSecretString: { passwordLength: 32, excludePunctuation: true, includeSpace: false },
    });

    // Model provider keys, as JSON {"openai": "...", "elevenlabs": "...", "meta": "..."} (voice and
    // imports). Created with empty values; the real keys are put in by hand (never in code or the
    // template) — see DEPLOY.md.
    const voiceKeys = new secretsmanager.Secret(this, 'VoiceKeys', {
      description: 'Chronova model provider keys: {"openai": "...", "elevenlabs": "...", "meta": "..."}',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ openai: '', elevenlabs: '', meta: '' }),
        generateStringKey: 'placeholder',
        excludePunctuation: true,
      },
    });
    const voiceOn = props.voice ?? false;
    const importOn = props.import ?? false;

    // The Firebase service account (docs/push.md): the whole key file's JSON, put in by hand
    // (never in code or the template) — see DEPLOY.md. Created with a placeholder.
    const pushKey = new secretsmanager.Secret(this, 'PushKey', {
      description: 'Chronova Firebase Cloud Messaging service-account JSON (the downloaded key file)',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ type: 'placeholder' }),
        generateStringKey: 'placeholder',
        excludePunctuation: true,
      },
    });
    const pushOn = props.push ?? false;

    // --- Email (SES) ----------------------------------------------------------------
    // A domain identity signs mail with Easy DKIM once its three CNAME records are in the domain's DNS.
    const mailIdentityName = props.mailDomain ? props.mailFrom.split('@')[1] : props.mailFrom;
    const mailIdentity = new ses.EmailIdentity(this, 'MailFrom', {
      identity: props.mailDomain ? ses.Identity.domain(mailIdentityName) : ses.Identity.email(mailIdentityName),
    });

    // --- Containers -----------------------------------------------------------------
    const cluster = new ecs.Cluster(this, 'Cluster', { vpc, containerInsightsV2: ecs.ContainerInsights.DISABLED });
    const image = ecs.ContainerImage.fromAsset(props.imageDirectory ?? path.join(__dirname, '..', '..'), {
      target: 'app',
      platform: ecrAssets.Platform.LINUX_ARM64,
    });

    const environment: Record<string, string> = {
      APP_ENV: 'production',
      APP_DEBUG: 'false',
      BEHIND_CLOUDFRONT: 'true',
      SESSION_SECURE_COOKIE: 'true',
      SESSION_DRIVER: 'database',
      CACHE_STORE: 'database',
      QUEUE_CONNECTION: 'database',
      DB_CONNECTION: 'pgsql',
      DB_SSLMODE: 'require',
      MAIL_MAILER: 'ses',
      MAIL_FROM_ADDRESS: props.mailFrom,
      MAIL_FROM_NAME: 'Chronova',
      AWS_DEFAULT_REGION: this.region,
      LOG_CHANNEL: 'stderr',
      // Off: every voice endpoint answers 503 and the app shows "switched off". On: real providers.
      VOICE_ENABLED: voiceOn ? 'true' : 'false',
      VOICE_TRANSCRIBER: voiceOn ? 'elevenlabs' : 'fake',
      VOICE_INTERPRETER: voiceOn ? 'openai' : 'fake',
      VOICE_EXPLAINER: voiceOn ? 'openai' : 'fake',
      VOICE_SPEECH: voiceOn ? 'elevenlabs' : 'fake',
      // Off: every import answers 503 and the app says it's switched off. On: Meta and OpenAI.
      IMPORT_ENABLED: importOn ? 'true' : 'false',
      IMPORT_ANALYZER: importOn ? 'meta' : 'fake',
      IMPORT_BUILDER: importOn ? 'openai' : 'fake',
      // Off: pushes are recorded, not sent (the app's own reminder alarm still works). On: Firebase.
      PUSH_SENDER: pushOn ? 'fcm' : 'fake',
    };
    const baseSecrets: Record<string, ecs.Secret> = {
      APP_KEY: ecs.Secret.fromSecretsManager(appKey),
      DB_HOST: ecs.Secret.fromSecretsManager(dbSecret, 'host'),
      DB_PORT: ecs.Secret.fromSecretsManager(dbSecret, 'port'),
      DB_DATABASE: ecs.Secret.fromSecretsManager(dbSecret, 'dbname'),
      DB_USERNAME: ecs.Secret.fromSecretsManager(dbSecret, 'username'),
      DB_PASSWORD: ecs.Secret.fromSecretsManager(dbSecret, 'password'),
    };
    // The containers that send pushes (web, worker, scheduler); migrations don't.
    const secrets: Record<string, ecs.Secret> = pushOn
      ? { ...baseSecrets, FCM_CREDENTIALS_JSON: ecs.Secret.fromSecretsManager(pushKey) }
      : baseSecrets;

    // The web container alone calls the model providers, and gets only the keys it uses.
    const webSecrets: Record<string, ecs.Secret> = {
      ...secrets,
      ...(voiceOn || importOn ? { OPENAI_API_KEY: ecs.Secret.fromSecretsManager(voiceKeys, 'openai') } : {}),
      ...(voiceOn ? { ELEVENLABS_API_KEY: ecs.Secret.fromSecretsManager(voiceKeys, 'elevenlabs') } : {}),
      ...(importOn ? { META_API_KEY: ecs.Secret.fromSecretsManager(voiceKeys, 'meta') } : {}),
    };

    const sendMail = new iam.PolicyStatement({
      actions: ['ses:SendEmail', 'ses:SendRawEmail'],
      resources: [`arn:${this.partition}:ses:${this.region}:${this.account}:identity/${mailIdentityName}`],
    });

    const taskDefinition = (id: string, cpu: number, memoryLimitMiB: number) =>
      new ecs.FargateTaskDefinition(this, id, {
        cpu,
        memoryLimitMiB,
        runtimePlatform: { cpuArchitecture: ecs.CpuArchitecture.ARM64, operatingSystemFamily: ecs.OperatingSystemFamily.LINUX },
      });
    const logDriver = (prefix: string) =>
      ecs.LogDrivers.awsLogs({
        streamPrefix: prefix,
        logGroup: new logs.LogGroup(this, `${prefix}Logs`, { retention: logs.RetentionDays.ONE_MONTH, removalPolicy: RemovalPolicy.DESTROY }),
      });

    // Web: serves the API and the admin panel.
    const webTask = taskDefinition('WebTask', 512, 1024);
    webTask.addContainer('web', {
      image,
      command: ['web'],
      environment,
      secrets: webSecrets,
      logging: logDriver('web'),
      portMappings: [{ containerPort: 8080 }],
      healthCheck: { command: ['CMD', 'curl', '-fsS', 'http://127.0.0.1:8080/up'], interval: Duration.seconds(15), startPeriod: Duration.seconds(30) },
    });
    webTask.addToTaskRolePolicy(sendMail);

    // Jobs: the queue worker (sends verification emails) and the scheduler, in one small task.
    const jobsTask = taskDefinition('JobsTask', 256, 512);
    jobsTask.addContainer('worker', { image, command: ['worker'], environment, secrets, logging: logDriver('worker') });
    jobsTask.addContainer('scheduler', { image, command: ['scheduler'], environment, secrets, logging: logDriver('scheduler') });
    jobsTask.addToTaskRolePolicy(sendMail);

    // Migrations: a one-off task, run before (or right after) each deploy — see DEPLOY.md.
    const migrateTask = taskDefinition('MigrateTask', 256, 512);
    migrateTask.addContainer('migrate', { image, command: ['migrate'], environment, secrets: baseSecrets, logging: logDriver('migrate') });

    const serviceSecurityGroup = new ec2.SecurityGroup(this, 'ServiceSg', { vpc, description: 'App tasks: inbound from the load balancer only' });
    dbSecurityGroup.addIngressRule(serviceSecurityGroup, ec2.Port.tcp(5432), 'App tasks to Postgres');

    const publicSubnets = { subnetType: ec2.SubnetType.PUBLIC };
    const web = new ecs.FargateService(this, 'Web', {
      cluster,
      taskDefinition: webTask,
      desiredCount: 1,
      assignPublicIp: true,
      vpcSubnets: publicSubnets,
      securityGroups: [serviceSecurityGroup],
      circuitBreaker: { rollback: true },
      minHealthyPercent: 100,
    });
    const jobs = new ecs.FargateService(this, 'Jobs', {
      cluster,
      taskDefinition: jobsTask,
      // Interactive admin commands (e.g. creating the first staff account) via `aws ecs execute-command`.
      enableExecuteCommand: true,
      desiredCount: 1,
      assignPublicIp: true,
      vpcSubnets: publicSubnets,
      securityGroups: [serviceSecurityGroup],
      circuitBreaker: { rollback: true },
      minHealthyPercent: 0,
    });
    const scaling = web.autoScaleTaskCount({ minCapacity: 1, maxCapacity: isProd ? 4 : 2 });
    scaling.scaleOnCpuUtilization('Cpu', { targetUtilizationPercent: 60 });

    // --- Load balancer (internal) + CloudFront ----------------------------------------
    const albSecurityGroup = new ec2.SecurityGroup(this, 'AlbSg', { vpc, description: 'Internal ALB: reached by CloudFront VPC origins' });
    const alb = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      vpc,
      internetFacing: false,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      securityGroup: albSecurityGroup,
      dropInvalidHeaderFields: true,
    });
    const listener = alb.addListener('Http', { port: 80, open: false });
    const webTargets = listener.addTargets('Web', {
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targets: [web],
      healthCheck: { path: '/up', healthyHttpCodes: '200', interval: Duration.seconds(15) },
      deregistrationDelay: Duration.seconds(30),
    });
    serviceSecurityGroup.addIngressRule(albSecurityGroup, ec2.Port.tcp(8080), 'Load balancer to web tasks');
    // The ALB has no internet path at all; CloudFront's VPC-origin interfaces sit inside the VPC.
    albSecurityGroup.addIngressRule(ec2.Peer.ipv4(vpc.vpcCidrBlock), ec2.Port.tcp(80), 'CloudFront VPC origins (inside the VPC)');

    // All viewer headers (incl. Host and Authorization — CloudFront only forwards Authorization
    // via an all-viewer policy or a cache key) plus CloudFront's client-address header.
    // Caching is disabled for these routes, so no response can be served to another user.
    const originRequests = new cloudfront.OriginRequestPolicy(this, 'OriginRequests', {
      comment: 'Chronova: all viewer headers, cookies and query strings, plus the client address',
      headerBehavior: cloudfront.OriginRequestHeaderBehavior.all('CloudFront-Viewer-Address'),
      cookieBehavior: cloudfront.OriginRequestCookieBehavior.all(),
      queryStringBehavior: cloudfront.OriginRequestQueryStringBehavior.all(),
    });
    const origin = origins.VpcOrigin.withApplicationLoadBalancer(alb, {
      protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY,
      httpPort: 80,
      // Reading an image (Meta, then OpenAI) can take most of a minute; 60 s is the most without a quota request.
      readTimeout: Duration.seconds(60),
    });
    const distribution = new cloudfront.Distribution(this, 'Cdn', {
      comment: `Chronova ${props.stage}`,
      priceClass: cloudfront.PriceClass.PRICE_CLASS_100,
      // The default *.cloudfront.net certificate fixes the TLS policy; a custom domain +
      // certificate is needed to enforce TLS 1.2+ (see DEPLOY.md, "Custom domain").
      defaultBehavior: {
        origin,
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
        originRequestPolicy: originRequests,
        responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
      },
      // Versioned admin-panel assets can be cached at the edge.
      additionalBehaviors: Object.fromEntries(
        ['css/*', 'js/*', 'fonts/*'].map((pattern) => [
          pattern,
          {
            origin,
            viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
            cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
          },
        ]),
      ),
    });

    // --- Alarms -----------------------------------------------------------------------
    const alarms = new sns.Topic(this, 'Alarms');
    if (props.alarmEmail) alarms.addSubscription(new subs.EmailSubscription(props.alarmEmail));
    const notify = new cwActions.SnsAction(alarms);
    for (const alarm of [
      new cloudwatch.Alarm(this, 'Server5xx', {
        alarmDescription: 'The app returned 5xx responses',
        metric: alb.metrics.httpCodeTarget(elbv2.HttpCodeTarget.TARGET_5XX_COUNT, { period: Duration.minutes(5), statistic: 'Sum' }),
        threshold: 5,
        evaluationPeriods: 1,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }),
      new cloudwatch.Alarm(this, 'NoHealthyWeb', {
        alarmDescription: 'No healthy web task behind the load balancer',
        metric: webTargets.metrics.healthyHostCount({ period: Duration.minutes(1), statistic: 'Minimum' }),
        threshold: 1,
        comparisonOperator: cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD,
        evaluationPeriods: 3,
        treatMissingData: cloudwatch.TreatMissingData.BREACHING,
      }),
      new cloudwatch.Alarm(this, 'DatabaseStorageLow', {
        alarmDescription: 'Less than 2 GB of database storage left',
        metric: database.metricFreeStorageSpace({ period: Duration.minutes(15) }),
        threshold: 2 * 1024 ** 3,
        comparisonOperator: cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD,
        evaluationPeriods: 1,
      }),
    ]) {
      alarm.addAlarmAction(notify);
    }

    // --- Outputs --------------------------------------------------------------------
    new CfnOutput(this, 'ApiUrl', { value: `https://${distribution.distributionDomainName}`, description: 'chronova.apiUrl for the Android app' });
    new CfnOutput(this, 'AdminUrl', { value: `https://${distribution.distributionDomainName}/admin` });
    new CfnOutput(this, 'ClusterName', { value: cluster.clusterName });
    new CfnOutput(this, 'MigrateTaskDefinition', { value: migrateTask.taskDefinitionArn });
    new CfnOutput(this, 'TaskSubnets', { value: vpc.publicSubnets.map((s) => s.subnetId).join(',') });
    new CfnOutput(this, 'TaskSecurityGroup', { value: serviceSecurityGroup.securityGroupId });
    new CfnOutput(this, 'MailIdentity', { value: mailIdentity.emailIdentityName });
    if (props.mailDomain) {
      // The records to add at the domain's DNS host (CNAME name → value); SES verifies the domain once they resolve.
      mailIdentity.dkimRecords.forEach((record, i) =>
        new CfnOutput(this, `DkimRecord${i + 1}`, { value: `${record.name} CNAME ${record.value}`, description: 'Add to the sending domain\'s DNS' }),
      );
    }
    new CfnOutput(this, 'JobsService', { value: jobs.serviceName });
    new CfnOutput(this, 'PushKeySecret', { value: pushKey.secretName, description: 'Put the Firebase service-account JSON here (DEPLOY.md)' });
    new CfnOutput(this, 'VoiceKeysSecret', { value: voiceKeys.secretName, description: 'Put the OpenAI and ElevenLabs keys here (DEPLOY.md)' });
  }
}
