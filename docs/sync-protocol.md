# Chronova sync protocol

The contract between the Android app (`data/remote/SyncApi.kt`, `data/sync/SyncEngine.kt`) and the server. It doesn't depend on how the server stores data. First implemented by the AWS serverless prototype (git tag `serverless-backend-v1`), now by the Laravel server in `server/`.

## Request and response

```
POST /v1/sync   Authorization: Bearer <access token>
{ "cursor": "42" | null,
  "changes": [ { "entity": "project" | "task" | "dependency", "id": "…", "deleted": false,
                 "updatedAt": 1767000000000, "data": { … } } ] }

200 { "results": [ { "entity", "id", "status": "applied" | "stale" } ],
      "changes": [ …same shape as pushed… ], "cursor": "57", "hasMore": false }
400 { "error": "…" }     invalid request; nothing is stored
401                      missing, expired or revoked token
409 { "error": "busy" }  another sync of the same user is running; retry shortly
```

## Rules

1. **Last write wins per record, by edit time** (`updatedAt`, epoch millis). A pushed change is stored only if it is newer than the stored copy; **ties keep the stored copy**.
2. **Clock clamp:** an `updatedAt` in the future is stored as the server's current time, so a device whose clock runs ahead can't win every conflict.
3. **Stale pushes return the winner:** for every `stale` result, `changes` also contains the server's current copy of that record — even when its sequence number is behind the client's cursor (a device with a slow clock may have pulled it already). Not repeated if it's already in the page.
4. **Cursor:** every stored write gets a per-user, strictly increasing sequence number (gaps allowed). The cursor is the last one the client has seen. `changes` holds records with a higher number, ascending, at most **500** per response; the client repeats with the new cursor (and no changes) while `hasMore` is true. With nothing new the cursor is returned unchanged.
5. **Serialised per user:** two syncs of the same user never interleave (they would otherwise leave a sequence gap a puller could skip).
6. **Tombstones:** deletes are `deleted: true` with no `data`; they replace the stored record under rule 1 and are pulled like any change.
7. **Isolation:** a user only ever reads and writes their own records. Record ids are only unique per user (e.g. every device seeds `sample-project`).
8. **At most 500 changes per request.** Unknown fields are dropped, never stored.

## Field rules

| Entity | `data` fields |
|---|---|
| all | `id`: 1–64 characters `[A-Za-z0-9_-]`; `updatedAt`: integer ≥ 0; `deleted`: boolean |
| project | `name` 1–200 chars (not blank); `startDate`, `endDate` `YYYY-MM-DD`, end ≥ start |
| task | `projectId` (id); `title` 1–200 chars; `startMillis`, `endMillis` integers ≥ 0, end > start; `color` `[A-Z]{1,20}`; `sortOrder` integer ≥ 0 |
| dependency | `projectId`, `predecessorId`, `successorId` (ids); predecessor ≠ successor |

## Conformance scenarios

Every server implementation is tested against these (the Jest suite at tag `serverless-backend-v1`, `backend/test/sync-core.test.ts`, is the reference):

stores pushed changes and returns them with a cursor · pulls only what changed after the cursor · last write wins, ties keep the server copy · returns the server copy of a stale push even when behind the cursor · clamps future edit times · stores tombstones without data · keeps users apart · pages large pulls · serialises concurrent syncs · rejects invalid requests without storing anything · accepts an empty pull · rejects malformed input (cursor, changes type, too many, entity, id, updatedAt, dates, times, self-dependency) · drops unknown fields.
