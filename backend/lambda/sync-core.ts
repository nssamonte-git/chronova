/**
 * Sync protocol, independent of AWS so it can be unit-tested.
 *
 * One request pushes the client's dirty records and pulls everything changed since
 * the client's cursor. Each record is resolved last-write-wins by its edit time
 * (`updatedAt`): a push is applied only if it is newer than the stored copy; ties
 * keep the server copy. Client clocks are clamped to the server clock so a device
 * with a clock in the future can't win every conflict forever.
 *
 * Every applied write gets a per-user, increasing sequence number; the cursor is the
 * last sequence number a client has seen. Sync requests of one user are serialised
 * by a short lock so two devices can't interleave sequence numbers and leave a gap
 * a puller would skip.
 */

export const ENTITIES = ['project', 'task', 'dependency'] as const;
export type Entity = (typeof ENTITIES)[number];

export interface Change {
  entity: Entity;
  id: string;
  deleted: boolean;
  /** Client edit time, epoch millis (clamped to the server clock when stored). */
  updatedAt: number;
  /** Entity fields; absent on tombstones. */
  data?: Record<string, string | number>;
}

export interface StoredChange extends Change {
  seq: number;
}

export interface SyncStore {
  /** Takes the user's sync lock until [until]; false if another sync holds it. */
  acquireLock(userId: string, now: number, until: number): Promise<boolean>;
  releaseLock(userId: string): Promise<void>;
  /** Next sequence number for the user (strictly increasing, gaps allowed). */
  nextSeq(userId: string): Promise<number>;
  /** Stores [change] unless the stored copy has an equal or newer `updatedAt`. Returns whether it was stored. */
  putIfNewer(userId: string, change: StoredChange): Promise<boolean>;
  /** Changes with seq > [cursor], ascending, at most [limit]. */
  changesSince(userId: string, cursor: number, limit: number): Promise<{ items: StoredChange[]; hasMore: boolean }>;
}

export interface SyncResponse {
  results: { entity: Entity; id: string; status: 'applied' | 'stale' }[];
  changes: Change[];
  cursor: string;
  hasMore: boolean;
}

export type SyncOutcome =
  | { status: 200; body: SyncResponse }
  | { status: 400; body: { error: string } }
  | { status: 409; body: { error: 'busy' } };

export const MAX_CHANGES = 500;
export const PAGE_SIZE = 500;
export const LOCK_MILLIS = 30_000;
const MAX_TEXT = 200;
const ID = /^[A-Za-z0-9_-]{1,64}$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

type Parsed = { cursor: number; changes: Change[] };

export async function sync(store: SyncStore, userId: string, body: unknown, now: number): Promise<SyncOutcome> {
  const parsed = parseRequest(body);
  if ('error' in parsed) return { status: 400, body: { error: parsed.error } };

  if (!(await store.acquireLock(userId, now, now + LOCK_MILLIS))) return { status: 409, body: { error: 'busy' } };
  try {
    const results: SyncResponse['results'] = [];
    for (const change of parsed.changes) {
      const stored: StoredChange = {
        ...change,
        updatedAt: Math.min(change.updatedAt, now),
        seq: await store.nextSeq(userId),
      };
      const applied = await store.putIfNewer(userId, stored);
      results.push({ entity: change.entity, id: change.id, status: applied ? 'applied' : 'stale' });
    }
    const page = await store.changesSince(userId, parsed.cursor, PAGE_SIZE);
    const last = page.items.length > 0 ? page.items[page.items.length - 1].seq : parsed.cursor;
    return {
      status: 200,
      body: {
        results,
        changes: page.items.map(({ seq: _seq, ...change }) => change),
        cursor: String(last),
        hasMore: page.hasMore,
      },
    };
  } finally {
    await store.releaseLock(userId);
  }
}

/** Validates the request and copies only known fields, so nothing unexpected is stored. */
export function parseRequest(body: unknown): Parsed | { error: string } {
  if (!isObject(body)) return { error: 'body must be an object' };
  let cursor = 0;
  if (body.cursor !== null && body.cursor !== undefined) {
    if (typeof body.cursor !== 'string' || !/^\d{1,15}$/.test(body.cursor)) return { error: 'invalid cursor' };
    cursor = Number(body.cursor);
  }
  const raw = body.changes ?? [];
  if (!Array.isArray(raw)) return { error: 'changes must be an array' };
  if (raw.length > MAX_CHANGES) return { error: `at most ${MAX_CHANGES} changes per request` };
  const changes: Change[] = [];
  for (const [index, item] of raw.entries()) {
    const change = parseChange(item);
    if (typeof change === 'string') return { error: `changes[${index}]: ${change}` };
    changes.push(change);
  }
  return { cursor, changes };
}

function parseChange(item: unknown): Change | string {
  if (!isObject(item)) return 'must be an object';
  const { entity, id, deleted, updatedAt } = item;
  if (typeof entity !== 'string' || !(ENTITIES as readonly string[]).includes(entity)) return 'unknown entity';
  if (typeof id !== 'string' || !ID.test(id)) return 'invalid id';
  if (typeof deleted !== 'boolean') return 'deleted must be a boolean';
  if (!isMillis(updatedAt)) return 'invalid updatedAt';
  const change: Change = { entity: entity as Entity, id, deleted, updatedAt };
  if (deleted) return change; // tombstones carry no data
  const data = parseData(entity as Entity, item.data);
  if (typeof data === 'string') return data;
  return { ...change, data };
}

function parseData(entity: Entity, data: unknown): Record<string, string | number> | string {
  if (!isObject(data)) return 'data is required unless deleted';
  switch (entity) {
    case 'project': {
      const { name, startDate, endDate } = data;
      if (!isText(name)) return 'invalid name';
      if (typeof startDate !== 'string' || !DATE.test(startDate)) return 'invalid startDate';
      if (typeof endDate !== 'string' || !DATE.test(endDate) || endDate < startDate) return 'invalid endDate';
      return { name, startDate, endDate };
    }
    case 'task': {
      const { projectId, title, startMillis, endMillis, color, sortOrder } = data;
      if (typeof projectId !== 'string' || !ID.test(projectId)) return 'invalid projectId';
      if (!isText(title)) return 'invalid title';
      if (!isMillis(startMillis) || !isMillis(endMillis) || endMillis <= startMillis) return 'invalid times';
      if (typeof color !== 'string' || !/^[A-Z]{1,20}$/.test(color)) return 'invalid color';
      if (typeof sortOrder !== 'number' || !Number.isInteger(sortOrder) || sortOrder < 0) return 'invalid sortOrder';
      return { projectId, title, startMillis, endMillis, color, sortOrder };
    }
    case 'dependency': {
      const { projectId, predecessorId, successorId } = data;
      for (const value of [projectId, predecessorId, successorId]) {
        if (typeof value !== 'string' || !ID.test(value)) return 'invalid ids';
      }
      if (predecessorId === successorId) return 'a task cannot depend on itself';
      return { projectId: projectId as string, predecessorId: predecessorId as string, successorId: successorId as string };
    }
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isText(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= MAX_TEXT;
}

function isMillis(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}
