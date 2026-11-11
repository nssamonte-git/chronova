import { ConditionalCheckFailedException, DynamoDBClient } from '@aws-sdk/client-dynamodb';
import {
  BatchWriteCommand,
  DeleteCommand,
  DynamoDBDocumentClient,
  GetCommand,
  PutCommand,
  QueryCommand,
  UpdateCommand,
} from '@aws-sdk/lib-dynamodb';
import type { Entity, StoredChange, SyncStore } from './sync-core';

/**
 * Single-table layout, all under the user's partition `USER#<sub>`:
 * - `ITEM#<entity>#<id>`: a record (with `seq`, so it appears in the `bySeq` index)
 * - `COUNTER`: the user's last sequence number
 * - `LOCK`: the sync lock (`lockedUntil`)
 * Counter and lock have no `seq`, so the sparse `bySeq` index holds only records.
 */
export class DynamoSyncStore implements SyncStore {
  constructor(
    private readonly tableName: string,
    // Tombstones have no `data`; drop undefined fields instead of failing to marshal them.
    private readonly db: DynamoDBDocumentClient = DynamoDBDocumentClient.from(new DynamoDBClient({}), {
      marshallOptions: { removeUndefinedValues: true },
    }),
  ) {}

  async acquireLock(userId: string, now: number, until: number): Promise<boolean> {
    try {
      await this.db.send(
        new PutCommand({
          TableName: this.tableName,
          Item: { pk: pk(userId), sk: 'LOCK', lockedUntil: until },
          ConditionExpression: 'attribute_not_exists(pk) OR lockedUntil < :now',
          ExpressionAttributeValues: { ':now': now },
        }),
      );
      return true;
    } catch (error) {
      if (error instanceof ConditionalCheckFailedException) return false;
      throw error;
    }
  }

  async releaseLock(userId: string): Promise<void> {
    await this.db.send(new DeleteCommand({ TableName: this.tableName, Key: { pk: pk(userId), sk: 'LOCK' } }));
  }

  async nextSeq(userId: string): Promise<number> {
    const result = await this.db.send(
      new UpdateCommand({
        TableName: this.tableName,
        Key: { pk: pk(userId), sk: 'COUNTER' },
        UpdateExpression: 'ADD #value :one',
        ExpressionAttributeNames: { '#value': 'value' },
        ExpressionAttributeValues: { ':one': 1 },
        ReturnValues: 'UPDATED_NEW',
      }),
    );
    return Number(result.Attributes?.value);
  }

  async putIfNewer(userId: string, change: StoredChange): Promise<boolean> {
    try {
      await this.db.send(
        new PutCommand({
          TableName: this.tableName,
          Item: { pk: pk(userId), sk: sk(change.entity, change.id), ...change },
          // Last write wins by edit time; ties keep the stored copy.
          ConditionExpression: 'attribute_not_exists(pk) OR updatedAt < :updatedAt',
          ExpressionAttributeValues: { ':updatedAt': change.updatedAt },
        }),
      );
      return true;
    } catch (error) {
      if (error instanceof ConditionalCheckFailedException) return false;
      throw error;
    }
  }

  async get(userId: string, entity: Entity, id: string): Promise<StoredChange | undefined> {
    const result = await this.db.send(new GetCommand({ TableName: this.tableName, Key: { pk: pk(userId), sk: sk(entity, id) } }));
    return result.Item ? toChange(result.Item) : undefined;
  }

  async changesSince(userId: string, cursor: number, limit: number) {
    const result = await this.db.send(
      new QueryCommand({
        TableName: this.tableName,
        IndexName: 'bySeq',
        KeyConditionExpression: 'pk = :pk AND seq > :cursor',
        ExpressionAttributeValues: { ':pk': pk(userId), ':cursor': cursor },
        ScanIndexForward: true,
        Limit: limit,
      }),
    );
    const items = (result.Items ?? []).map(toChange);
    return { items, hasMore: result.LastEvaluatedKey !== undefined };
  }

  /** Deletes every item in the user's partition (records, counter, lock). */
  async deleteAllFor(userId: string): Promise<number> {
    let deleted = 0;
    let startKey: Record<string, unknown> | undefined;
    do {
      const page = await this.db.send(
        new QueryCommand({
          TableName: this.tableName,
          KeyConditionExpression: 'pk = :pk',
          ExpressionAttributeValues: { ':pk': pk(userId) },
          ProjectionExpression: 'pk, sk',
          ExclusiveStartKey: startKey,
        }),
      );
      const keys = (page.Items ?? []).map((item) => ({ pk: item.pk, sk: item.sk }));
      for (let i = 0; i < keys.length; i += 25) {
        let requests = keys.slice(i, i + 25).map((Key) => ({ DeleteRequest: { Key } }));
        // Retry whatever DynamoDB reports as unprocessed (throttling), with a short backoff.
        for (let attempt = 0; requests.length > 0; attempt++) {
          if (attempt > 0) await new Promise((resolve) => setTimeout(resolve, 50 * 2 ** Math.min(attempt, 5)));
          const result = await this.db.send(new BatchWriteCommand({ RequestItems: { [this.tableName]: requests } }));
          requests = (result.UnprocessedItems?.[this.tableName] ?? []) as typeof requests;
          if (attempt > 8) throw new Error('could not delete all items');
        }
      }
      deleted += keys.length;
      startKey = page.LastEvaluatedKey;
    } while (startKey);
    return deleted;
  }
}

function toChange(item: Record<string, any>): StoredChange {
  return {
    entity: item.entity,
    id: item.id,
    deleted: item.deleted,
    updatedAt: item.updatedAt,
    ...(item.data ? { data: item.data } : {}),
    seq: item.seq,
  };
}

const pk = (userId: string) => `USER#${userId}`;
const sk = (entity: Entity, id: string) => `ITEM#${entity}#${id}`;
