import type { StoredChange, SyncStore } from '../lambda/sync-core';

/** In-memory SyncStore with the same semantics as the DynamoDB one. */
export class MemoryStore implements SyncStore {
  readonly items = new Map<string, Map<string, StoredChange>>();
  readonly locks = new Map<string, number>();
  private readonly counters = new Map<string, number>();

  private userItems(userId: string) {
    let items = this.items.get(userId);
    if (!items) this.items.set(userId, (items = new Map()));
    return items;
  }

  async acquireLock(userId: string, now: number, until: number) {
    const held = this.locks.get(userId);
    if (held !== undefined && held >= now) return false;
    this.locks.set(userId, until);
    return true;
  }

  async releaseLock(userId: string) {
    this.locks.delete(userId);
  }

  async nextSeq(userId: string) {
    const next = (this.counters.get(userId) ?? 0) + 1;
    this.counters.set(userId, next);
    return next;
  }

  async putIfNewer(userId: string, change: StoredChange) {
    const key = `${change.entity}#${change.id}`;
    const existing = this.userItems(userId).get(key);
    if (existing && existing.updatedAt >= change.updatedAt) return false;
    this.userItems(userId).set(key, change);
    return true;
  }

  async changesSince(userId: string, cursor: number, limit: number) {
    const all = [...this.userItems(userId).values()].filter((c) => c.seq > cursor).sort((a, b) => a.seq - b.seq);
    return { items: all.slice(0, limit), hasMore: all.length > limit };
  }
}
