import { App } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import { ChronovaStack } from '../lib/chronova-stack';

// Bundling runs esbuild for each function, so the template is built once.
const template = Template.fromStack(new ChronovaStack(new App(), 'Test', { stage: 'dev' }));

describe('ChronovaStack', () => {
  it('creates one pay-per-request table with point-in-time recovery and the bySeq index', () => {
    template.resourceCountIs('AWS::DynamoDB::Table', 1);
    template.hasResourceProperties('AWS::DynamoDB::Table', {
      BillingMode: 'PAY_PER_REQUEST',
      PointInTimeRecoverySpecification: { PointInTimeRecoveryEnabled: true },
      SSESpecification: { SSEEnabled: true },
      GlobalSecondaryIndexes: [Match.objectLike({ IndexName: 'bySeq', Projection: { ProjectionType: 'ALL' } })],
    });
  });

  it('lets users sign up with email and gives the app a public SRP client', () => {
    template.hasResourceProperties('AWS::Cognito::UserPool', {
      UsernameAttributes: ['email'],
      AutoVerifiedAttributes: ['email'],
    });
    template.hasResourceProperties('AWS::Cognito::UserPoolClient', {
      GenerateSecret: false,
      ExplicitAuthFlows: Match.arrayWith(['ALLOW_USER_SRP_AUTH', 'ALLOW_REFRESH_TOKEN_AUTH']),
      PreventUserExistenceErrors: 'ENABLED',
    });
  });

  it('protects both routes with the Cognito JWT authorizer', () => {
    template.hasResourceProperties('AWS::ApiGatewayV2::Authorizer', { AuthorizerType: 'JWT' });
    for (const routeKey of ['POST /v1/sync', 'DELETE /v1/account']) {
      template.hasResourceProperties('AWS::ApiGatewayV2::Route', { RouteKey: routeKey, AuthorizationType: 'JWT' });
    }
    template.hasResourceProperties('AWS::ApiGatewayV2::Stage', {
      StageName: '$default',
      DefaultRouteSettings: { ThrottlingRateLimit: 20, ThrottlingBurstLimit: 40 },
    });
  });

  it('runs both functions on Node.js 22 with log retention', () => {
    template.resourcePropertiesCountIs('AWS::Lambda::Function', { Runtime: 'nodejs22.x', Architectures: ['arm64'] }, 2);
    template.allResourcesProperties('AWS::Logs::LogGroup', { RetentionInDays: 30 });
  });

  it('allows only the delete-account function to delete Cognito users, and only in this pool', () => {
    const policies = template.findResources('AWS::IAM::Policy');
    const withAdminDelete = Object.values(policies).filter((policy) =>
      JSON.stringify(policy).includes('cognito-idp:AdminDeleteUser'),
    );
    expect(withAdminDelete).toHaveLength(1);
    expect(JSON.stringify(withAdminDelete[0])).not.toContain('"Resource":"*"');
  });

  it('grants each function only the DynamoDB calls it makes', () => {
    const actionsOf = (prefix: string) => {
      const [policy] = Object.entries(template.findResources('AWS::IAM::Policy')).filter(([id]) => id.startsWith(prefix));
      const statements = (policy[1] as { Properties: { PolicyDocument: { Statement: { Action: string | string[] }[] } } })
        .Properties.PolicyDocument.Statement;
      return statements.flatMap((statement) => [statement.Action].flat()).filter((action) => action.startsWith('dynamodb:')).sort();
    };
    expect(actionsOf('Sync')).toEqual(['dynamodb:DeleteItem', 'dynamodb:PutItem', 'dynamodb:Query', 'dynamodb:UpdateItem']);
    expect(actionsOf('DeleteAccount')).toEqual(['dynamodb:BatchWriteItem', 'dynamodb:Query']);
  });
});
