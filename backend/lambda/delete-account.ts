import { AdminDeleteUserCommand, CognitoIdentityProviderClient } from '@aws-sdk/client-cognito-identity-provider';
import type { APIGatewayProxyEventV2WithJWTAuthorizer, APIGatewayProxyResultV2 } from 'aws-lambda';
import { deleteAccount } from './account-core';
import { DynamoSyncStore } from './dynamo-store';
import { json, userIdOf } from './http';

const store = new DynamoSyncStore(requireEnv('TABLE_NAME'));
const cognito = new CognitoIdentityProviderClient({});
const userPoolId = requireEnv('USER_POOL_ID');

/** DELETE /v1/account — deletes all of the caller's synced data, then their Cognito user. */
export async function handler(event: APIGatewayProxyEventV2WithJWTAuthorizer): Promise<APIGatewayProxyResultV2> {
  const userId = userIdOf(event);
  // Access tokens carry the Cognito username in `username`.
  const username = event.requestContext.authorizer?.jwt?.claims?.username;
  if (!userId || typeof username !== 'string') return json(401, { error: 'unauthorized' });

  const result = await deleteAccount(
    store,
    { deleteUser: async (name) => void (await cognito.send(new AdminDeleteUserCommand({ UserPoolId: userPoolId, Username: name }))) },
    userId,
    username,
  );
  console.log(JSON.stringify({ deletedItems: result.deleted }));
  return json(200, { deleted: true });
}

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is not set`);
  return value;
}
