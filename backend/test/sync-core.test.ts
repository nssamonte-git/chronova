import { MAX_CHANGES, PAGE_SIZE, parseRequest, sync, type Change } from '../lambda/sync-core';
import { MemoryStore } from './memory-store';

const NOW = 1_800_000_000_000;

const project = (id: string, updatedAt: number, name = 'Launch'): Change => ({
  entity: 'project',
  id,
  deleted: false,
  updatedAt,
  data: { name, startDate: '2026-03-02', endDate: '2026-03-04' },
});

const task = (id: string, updatedAt: number, title = 'Design'): Change => ({
  entity: 'task',
  id,
  deleted: false,
  updatedAt,
  data: { projectId: 'p1', title, startMillis: 1000, endMillis: 2000, color: 'BLUE', sortOrder: 0 },
});

async function ok(store: MemoryStore, user: string, body: unknown, now = NOW) {
  const outcome = await sync(store, user, body, now);
  if (outcome.status !== 200) throw new Error(`expected 200, got ${outcome.status}: ${JSON.stringify(outcome.body)}`);
  return outcome.body;
}

describe('sync', () => {
  it('stores pushed changes and returns them with a cursor', async () => {
    const store = new MemoryStore();
    const body = await ok(store, 'alice', { cursor: null, changes: [project('p1', NOW - 10), task('t1', NOW - 5)] });
    expect(body.results).toEqual([
      { entity: 'project', id: 'p1', status: 'applied' },
      { entity: 'task', id: 't1', status: 'applied' },
    ]);
    expect(body.changes.map((c) => c.id)).toEqual(['p1', 't1']);
    expect(body.cursor).toBe('2');
    expect(body.hasMore).toBe(false);
  });

  it('pulls only what changed after the cursor', async () => {
    const store = new MemoryStore();
    const first = await ok(store, 'alice', { changes: [project('p1', NOW - 10)] });
    await ok(store, 'alice', { changes: [task('t1', NOW - 5)] }); // another device
    const pull = await ok(store, 'alice', { cursor: first.cursor });
    expect(pull.changes.map((c) => c.id)).toEqual(['t1']);
    expect(pull.cursor).toBe('2');
    // Nothing new: the cursor stays where it was.
    const again = await ok(store, 'alice', { cursor: pull.cursor });
    expect(again.changes).toEqual([]);
    expect(again.cursor).toBe('2');
  });

  it('last write wins by edit time, and ties keep the server copy', async () => {
    const store = new MemoryStore();
    await ok(store, 'alice', { changes: [task('t1', NOW - 100, 'Server')] });
    const older = await ok(store, 'alice', { changes: [task('t1', NOW - 200, 'Older edit')] });
    expect(older.results[0].status).toBe('stale');
    const tie = await ok(store, 'alice', { changes: [task('t1', NOW - 100, 'Same time')] });
    expect(tie.results[0].status).toBe('stale');
    const newer = await ok(store, 'alice', { changes: [task('t1', NOW - 50, 'Newer edit')] });
    expect(newer.results[0].status).toBe('applied');
    expect(store.items.get('alice')!.get('task#t1')!.data!.title).toBe('Newer edit');
  });

  it('returns the server copy of a stale push even when it is behind the cursor', async () => {
    const store = new MemoryStore();
    await ok(store, 'alice', { changes: [task('t1', NOW - 100, 'Server')] });
    const pulled = await ok(store, 'alice', { cursor: null }); // this device is now past seq 1
    // A later push with an older edit time (slow device clock) loses...
    const stale = await ok(store, 'alice', { cursor: pulled.cursor, changes: [task('t1', NOW - 500, 'Slow clock')] });
    expect(stale.results[0].status).toBe('stale');
    // ...and the response says what won, although its seq is behind the cursor.
    expect(stale.changes).toEqual([task('t1', NOW - 100, 'Server')]);
  });

  it('clamps future edit times to the server clock', async () => {
    const store = new MemoryStore();
    await ok(store, 'alice', { changes: [task('t1', NOW + 86_400_000, 'Clock ahead')] });
    expect(store.items.get('alice')!.get('task#t1')!.updatedAt).toBe(NOW);
    // A later honest edit can still win.
    const later = await ok(store, 'alice', { changes: [task('t1', NOW + 1000, 'Honest')] }, NOW + 1000);
    expect(later.results[0].status).toBe('applied');
  });

  it('stores tombstones without data', async () => {
    const store = new MemoryStore();
    await ok(store, 'alice', { changes: [task('t1', NOW - 10)] });
    const body = await ok(store, 'alice', { changes: [{ entity: 'task', id: 't1', deleted: true, updatedAt: NOW - 5 }] });
    expect(body.results[0].status).toBe('applied');
    expect(store.items.get('alice')!.get('task#t1')).toMatchObject({ deleted: true });
    expect(store.items.get('alice')!.get('task#t1')!.data).toBeUndefined();
  });

  it('keeps users apart', async () => {
    const store = new MemoryStore();
    await ok(store, 'alice', { changes: [project('p1', NOW - 10)] });
    const bob = await ok(store, 'bob', { cursor: null });
    expect(bob.changes).toEqual([]);
    expect(store.items.get('bob')?.size ?? 0).toBe(0);
  });

  it('pages large pulls', async () => {
    const store = new MemoryStore();
    const changes = Array.from({ length: PAGE_SIZE + 10 }, (_, i) => task(`t${i}`, NOW - 1));
    await ok(store, 'alice', { changes: changes.slice(0, MAX_CHANGES) });
    await ok(store, 'alice', { changes: changes.slice(MAX_CHANGES) });
    const page1 = await ok(store, 'alice', { cursor: null });
    expect(page1.changes).toHaveLength(PAGE_SIZE);
    expect(page1.hasMore).toBe(true);
    const page2 = await ok(store, 'alice', { cursor: page1.cursor });
    expect(page2.changes).toHaveLength(10);
    expect(page2.hasMore).toBe(false);
  });

  it('refuses a concurrent sync of the same user, then releases the lock', async () => {
    const store = new MemoryStore();
    store.locks.set('alice', NOW + 1000);
    expect((await sync(store, 'alice', {}, NOW)).status).toBe(409);
    // An expired lock is taken over.
    expect((await sync(store, 'alice', {}, NOW + 2000)).status).toBe(200);
    expect(store.locks.has('alice')).toBe(false);
  });

  it('rejects invalid requests without touching the store', async () => {
    const store = new MemoryStore();
    const bad = await sync(store, 'alice', { changes: [{ ...task('t1', NOW), data: { title: 'x' } }] }, NOW);
    expect(bad.status).toBe(400);
    expect(store.items.size).toBe(0);
    expect(store.locks.size).toBe(0);
  });
});

describe('parseRequest', () => {
  const error = (body: unknown) => {
    const parsed = parseRequest(body);
    return 'error' in parsed ? parsed.error : undefined;
  };

  it('accepts an empty pull', () => {
    expect(parseRequest({})).toEqual({ cursor: 0, changes: [] });
  });

  it('rejects malformed input', () => {
    expect(error(null)).toBeDefined();
    expect(error({ cursor: 'abc' })).toBe('invalid cursor');
    expect(error({ changes: {} })).toBe('changes must be an array');
    expect(error({ changes: Array(MAX_CHANGES + 1).fill(task('t', 1)) })).toContain('at most');
    expect(error({ changes: [{ ...task('t', 1), entity: 'user' }] })).toContain('unknown entity');
    expect(error({ changes: [{ ...task('t', 1), id: '../x' }] })).toContain('invalid id');
    expect(error({ changes: [{ ...task('t', 1), updatedAt: -1 }] })).toContain('invalid updatedAt');
    expect(error({ changes: [{ ...project('p', 1), data: { name: 'A', startDate: '2026-03-04', endDate: '2026-03-02' } }] })).toContain('endDate');
    expect(error({ changes: [{ ...task('t', 1), data: { ...task('t', 1).data, endMillis: 500 } }] })).toContain('times');
    expect(
      error({ changes: [{ entity: 'dependency', id: 'd', deleted: false, updatedAt: 1, data: { projectId: 'p', predecessorId: 'a', successorId: 'a' } }] }),
    ).toContain('itself');
  });

  it('drops unknown fields', () => {
    const parsed = parseRequest({ changes: [{ ...project('p', 1), data: { ...project('p', 1).data, owner: 'mallory' }, extra: true }] });
    expect('error' in parsed).toBe(false);
    if (!('error' in parsed)) {
      expect(parsed.changes[0]).toEqual(project('p', 1));
    }
  });
});
