# Chronova backend

Sync backend for the Chronova Android app, defined with AWS CDK (TypeScript):

- **Amazon Cognito** user pool (email sign-in) with a public SRP app client for the app.
- **API Gateway HTTP API** with a Cognito JWT authorizer and `$default`-stage throttling (20 req/s, burst 40):
  - `POST /v1/sync`: push local changes, pull changes since a cursor.
  - `DELETE /v1/account`: delete the caller's synced data, then their Cognito user.
- **AWS Lambda** (Node.js 22, arm64), one function per route, least-privilege IAM.
- **Amazon DynamoDB**, one on-demand table with point-in-time recovery.

## Sync protocol

```
POST /v1/sync   Authorization: Bearer <Cognito access token>
{ "cursor": "42" | null,
  "changes": [ { "entity": "project" | "task" | "dependency", "id": "…", "deleted": false,
                 "updatedAt": 1767000000000, "data": { … } } ] }

200 { "results": [ { "entity", "id", "status": "applied" | "stale" } ],
      "changes": [ …same shape as pushed… ], "cursor": "57", "hasMore": false }
400 { "error": "…" }   invalid request (nothing stored)
409 { "error": "busy" } another sync of the same user is running; retry shortly
```

- **Last write wins per record by edit time** (`updatedAt`). A pushed change is stored only if it is newer than the stored copy; ties keep the stored copy. Edit times in the future are clamped to the server clock. When a push is `stale`, `changes` also includes the server's current copy of that record, even if its `seq` is behind the cursor, so the client can replace its losing edit.
- Every stored write gets a per-user, increasing `seq`; the cursor is the last `seq` the client has seen. Pull pages hold at most 500 changes; repeat with the new cursor while `hasMore` is true.
- Syncs of one user are serialised with a 30-second lock item, so two devices can't interleave sequence numbers and leave a gap.
- Deletes are tombstones (`deleted: true`, no `data`).
- Field rules: ids are 1–64 characters `[A-Za-z0-9_-]`; names and titles are 1–200 characters; at most 500 changes per request. Unknown fields are dropped.

Data layout (partition `USER#<cognito sub>`): `ITEM#<entity>#<id>` records, `COUNTER`, `LOCK`. The sparse `bySeq` index (`pk`, `seq`) holds records only.

## Develop

```bash
npm ci
npm run build   # type-check
npm test        # Jest: sync protocol, account deletion, stack assertions
npm run synth   # CloudFormation template in cdk.out/
```

## Deploy (dev)

Requires AWS credentials for the target account and region (for example `aws configure --profile chronova`).

```bash
npx cdk bootstrap --profile chronova          # once per account and region
npx cdk deploy -c stage=dev --profile chronova
```

The stack prints `Region`, `UserPoolId`, `UserPoolClientId` and `ApiUrl`; the Android app reads them from its local config (see the app README). `stage=prod` keeps the user pool and table on stack deletion and turns on deletion protection; `dev` removes them.

Tear down a dev stack with `npx cdk destroy -c stage=dev --profile chronova`.

## Notes

- `npm audit --omit=dev` reports two moderate findings in a `minimatch` copy bundled inside `aws-cdk-lib`. It runs only at synth time on the developer machine and is not part of the Lambda bundles; npm's suggested fix is a major downgrade, so it is left as is.
- CDK reports feature flags that aren't set (the project wasn't generated with `cdk init`); review them with `npx cdk flags --unstable=flags` before a production deploy.
