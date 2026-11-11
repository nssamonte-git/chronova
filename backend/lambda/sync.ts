import type { APIGatewayProxyEventV2WithJWTAuthorizer, APIGatewayProxyResultV2 } from 'aws-lambda';
import { DynamoSyncStore } from './dynamo-store';
import { bodyOf, json, userIdOf } from './http';
import { sync } from './sync-core';

const store = new DynamoSyncStore(requireEnv('TABLE_NAME'));

/** POST /v1/sync — push local changes, pull changes since the cursor. */
export async function handler(event: APIGatewayProxyEventV2WithJWTAuthorizer): Promise<APIGatewayProxyResultV2> {
  const userId = userIdOf(event);
  if (!userId) return json(401, { error: 'unauthorized' });
  const body = bodyOf(event);
  if (body === undefined) return json(400, { error: 'body is not valid JSON' });

  const outcome = await sync(store, userId, body, Date.now());
  // Counts only: never log record contents or user identifiers.
  if (outcome.status === 200) {
    console.log(JSON.stringify({ pushed: outcome.body.results.length, pulled: outcome.body.changes.length }));
  } else {
    console.log(JSON.stringify({ status: outcome.status }));
  }
  return json(outcome.status, outcome.body);
}

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is not set`);
  return value;
}
