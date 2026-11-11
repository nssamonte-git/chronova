import * as path from 'node:path';
import { CfnOutput, Duration, RemovalPolicy, Stack, type StackProps } from 'aws-cdk-lib';
import { HttpApi, HttpMethod, HttpStage } from 'aws-cdk-lib/aws-apigatewayv2';
import { HttpUserPoolAuthorizer } from 'aws-cdk-lib/aws-apigatewayv2-authorizers';
import { HttpLambdaIntegration } from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Architecture, Runtime } from 'aws-cdk-lib/aws-lambda';
import { NodejsFunction, OutputFormat } from 'aws-cdk-lib/aws-lambda-nodejs';
import { LogGroup, RetentionDays } from 'aws-cdk-lib/aws-logs';
import type { Construct } from 'constructs';

export interface ChronovaStackProps extends StackProps {
  /** e.g. "dev" or "prod"; prod keeps the user pool and table if the stack is deleted. */
  readonly stage: string;
}

/**
 * Chronova sync backend:
 * - Cognito user pool (email sign-in) and a public app client for the Android app.
 * - HTTP API with a Cognito JWT authorizer: POST /v1/sync, DELETE /v1/account.
 * - One DynamoDB table, partitioned by user, with a sparse `bySeq` index for pulls.
 */
export class ChronovaStack extends Stack {
  constructor(scope: Construct, id: string, props: ChronovaStackProps) {
    super(scope, id, props);
    const isProd = props.stage === 'prod';
    const removalPolicy = isProd ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY;

    const userPool = new cognito.UserPool(this, 'Users', {
      userPoolName: `chronova-${props.stage}`,
      selfSignUpEnabled: true,
      signInAliases: { email: true },
      autoVerify: { email: true },
      standardAttributes: { email: { required: true, mutable: true } },
      passwordPolicy: { minLength: 10, requireLowercase: true, requireUppercase: true, requireDigits: true, requireSymbols: false },
      accountRecovery: cognito.AccountRecovery.EMAIL_ONLY,
      deletionProtection: isProd,
      removalPolicy,
    });

    // A mobile app can't keep a secret, so the client has none; SRP keeps the password off the wire.
    const appClient = userPool.addClient('AndroidApp', {
      userPoolClientName: 'android',
      generateSecret: false,
      authFlows: { userSrp: true },
      preventUserExistenceErrors: true,
      enableTokenRevocation: true,
      accessTokenValidity: Duration.hours(1),
      idTokenValidity: Duration.hours(1),
      refreshTokenValidity: Duration.days(30),
    });

    const table = new dynamodb.Table(this, 'Data', {
      partitionKey: { name: 'pk', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'sk', type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      encryption: dynamodb.TableEncryption.AWS_MANAGED,
      pointInTimeRecoverySpecification: { pointInTimeRecoveryEnabled: true },
      deletionProtection: isProd,
      removalPolicy,
    });
    // Only records have `seq`, so the counter and lock items stay out of the index.
    table.addGlobalSecondaryIndex({
      indexName: 'bySeq',
      partitionKey: { name: 'pk', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'seq', type: dynamodb.AttributeType.NUMBER },
      projectionType: dynamodb.ProjectionType.ALL,
    });

    // Least privilege: exactly the calls each function makes (on the table and its index).
    const syncFn = this.lambda('Sync', 'sync.ts', { TABLE_NAME: table.tableName });
    table.grant(syncFn, 'dynamodb:PutItem', 'dynamodb:UpdateItem', 'dynamodb:DeleteItem', 'dynamodb:Query');

    const deleteAccountFn = this.lambda('DeleteAccount', 'delete-account.ts', {
      TABLE_NAME: table.tableName,
      USER_POOL_ID: userPool.userPoolId,
    });
    table.grant(deleteAccountFn, 'dynamodb:Query', 'dynamodb:BatchWriteItem');
    deleteAccountFn.addToRolePolicy(
      new iam.PolicyStatement({ actions: ['cognito-idp:AdminDeleteUser'], resources: [userPool.userPoolArn] }),
    );

    const api = new HttpApi(this, 'Api', {
      apiName: `chronova-${props.stage}`,
      createDefaultStage: false,
      // No CORS configuration: only the Android app calls this API, so browsers get no cross-origin access.
      defaultAuthorizer: new HttpUserPoolAuthorizer('Cognito', userPool, { userPoolClients: [appClient] }),
    });
    new HttpStage(this, 'DefaultStage', {
      httpApi: api,
      stageName: '$default',
      autoDeploy: true,
      throttle: { rateLimit: 20, burstLimit: 40 },
    });
    api.addRoutes({ path: '/v1/sync', methods: [HttpMethod.POST], integration: new HttpLambdaIntegration('SyncIntegration', syncFn) });
    api.addRoutes({
      path: '/v1/account',
      methods: [HttpMethod.DELETE],
      integration: new HttpLambdaIntegration('DeleteAccountIntegration', deleteAccountFn),
    });

    new CfnOutput(this, 'Region', { value: this.region });
    new CfnOutput(this, 'UserPoolId', { value: userPool.userPoolId });
    new CfnOutput(this, 'UserPoolClientId', { value: appClient.userPoolClientId });
    new CfnOutput(this, 'ApiUrl', { value: api.apiEndpoint });
  }

  private lambda(id: string, file: string, environment: Record<string, string>): NodejsFunction {
    return new NodejsFunction(this, id, {
      entry: path.join(__dirname, '..', 'lambda', file),
      handler: 'handler',
      runtime: Runtime.NODEJS_22_X,
      architecture: Architecture.ARM_64,
      memorySize: 256,
      timeout: Duration.seconds(10),
      environment,
      logGroup: new LogGroup(this, `${id}Logs`, { retention: RetentionDays.ONE_MONTH, removalPolicy: RemovalPolicy.DESTROY }),
      // The Node.js runtime ships AWS SDK v3, so it is not bundled.
      bundling: { format: OutputFormat.CJS, minify: true, sourceMap: true, externalModules: ['@aws-sdk/*'] },
    });
  }
}
