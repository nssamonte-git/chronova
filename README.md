# Chronova — Shape Your Time

Chronova is an offline-first project scheduler: a native Android app (Kotlin, Jetpack Compose) with a Gantt-style timeline, a Laravel server it syncs with, and a staff admin panel.

- **App** — create projects with a date range, add tasks and finish-to-start dependencies, and edit them on a timeline by dragging and resizing bars (Hours, Days, Weeks or Months). Everything works offline; when the phone comes back online with unsynced changes it **asks** before syncing.
- **Server** (`server/`) — Laravel 13 on Postgres: sign-up with an emailed code, Sanctum tokens, and a sync API (last write wins per record).
- **Admin** (`/admin`) — Filament 5 panel for staff with required MFA: user management, a health dashboard, an audit log, and time-limited, audited support access to a user's projects.
- **AWS** (`server/infra/`) — CDK for CloudFront → private load balancer → ECS Fargate, RDS Postgres and SES. Not deployed yet; see [`server/infra/DEPLOY.md`](server/infra/DEPLOY.md).

It started as the PicCollage take-home (a single-screen timeline); that submission is preserved at git tag `submission-v1`. No third-party timeline or Gantt library is used.

## Features

### Timeline (the original take-home requirements, all kept)

| Requirement | Where |
|---|---|
| Header with readable labels and grid lines | `TimelineHeader`, `TimelineGrid`, `TimelineMath.ticks` (calendar-based) |
| Header and content scroll in sync; fixed name column | `SchedulerTimeline`, one `TimelineViewportState` |
| Tasks as bars in rows, positioned from the time scale | `TaskLane`, `TaskBar`, `TimelineMath` |
| Drag to move (keeps duration) | `detectTaskGestures`, `TaskEdits.move` |
| Resize from either edge | `HandleHitTest`, `TaskEdits.resizeStart` / `resizeEnd` |
| Snapping, both directions, no cumulative error | `SnapGrid` (`FixedGrid`, `DayGrid`), `TaskEdits` |
| Configurable scale, zoom, snap and row height (saved per project) | `TimelineControls`, `TimelineConfig`, `view_settings` table |
| Sample data on first launch | `SampleTasks` |

### Beyond the take-home

- **Projects**: create, rename, set an inclusive date range (up to 366 days; can't shrink past existing tasks), delete.
- **Tasks**: add, edit (title, colour, start/end date and time), delete with undo.
- **Scales**: Hours, Days, Weeks, Months; ticks follow the real calendar (DST days, month lengths) and whole-day snapping follows local midnights.
- **Dependencies**: finish-to-start links chosen in the task editor (cycles are blocked); arrows on the timeline turn red when violated.
- **Offline-first sync**: local Room database; a reconnect prompt ("You're back online — sync now?"), a sync button with the number of unsynced changes, last-write-wins conflict handling, sign-out/delete account clear the device.
- **Accounts**: sign up with an emailed 6-digit code, sign in, forgot/reset password, delete account. The token is encrypted with an Android Keystore key.

## Repository layout

```
.                       Android app (single Gradle module `app`)
├── app/
├── docs/
│   ├── sync-protocol.md        the app ↔ server contract
│   └── AI_USAGE_LOG.md         per-batch development log
└── server/                     Laravel app (API + admin panel), Dockerfile, compose.yaml
    └── infra/                  AWS CDK + DEPLOY.md
```

## Running the app

Requirements: Android Studio with AGP 9.5 support, JDK 17+, Android SDK 37. `minSdk` 26.

```bash
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Without a server the app works fully offline and the account screen says sync isn't set up. To sync, set the server URL in the uncommitted `local.properties`:

```properties
chronova.apiUrl=https://<your server>          # release builds need https
# or, for a debug build against the local server:
chronova.apiUrl=http://localhost:8000
```

For the local server on your machine, forward the port to a USB-connected phone: `adb reverse tcp:8000 tcp:8000` (debug builds allow plain HTTP to `localhost` only).

## Running the server

Requirements: PHP 8.4+ with `pdo_pgsql`, Composer 2, Docker. Details in [`server/README.md`](server/README.md).

```bash
cd server
docker compose up -d          # Postgres (:5433, + chronova_test) and Mailpit (mail UI on :8025)
composer setup                # install, .env, app key, migrate
php artisan serve --port=8000 & php artisan queue:work   # API + queue worker (sends codes)
php artisan chronova:create-staff you@example.com --role=admin   # first admin for /admin
composer test                 # Pest (103 tests)
```

## Architecture

### App

Single module with package boundaries and manual dependency injection (`ChronovaApp` / `AppContainer`):

```
noel.chronova.app
├── core/timeline     pure Kotlin: time ↔ pixels, calendar ticks, snap grids, edits, hit testing, viewport math
├── domain            models (Project, SchedulerTask, TaskDependency, TimelineConfig), rules (projects, tasks, dependencies)
├── data/local        Room: projects, tasks, dependencies, view_settings (schema v3, with migrations)
├── data/repository   RoomProjectRepository (every write stamps updatedAt and marks the row dirty)
├── data/remote       OkHttp sync client + DTOs
├── data/sync         SyncEngine, RoomLocalSyncStore, SyncCoordinator (reconnect prompt), connectivity
├── auth              ApiAuthRepository, Keystore token store, backend config
├── navigation        project list → timeline, account
└── presentation      projects, timeline (+ task editor), account, sync UI, components
```

- **Local first.** Screens read Room through repository `Flow`s; edits are written locally and marked dirty. Deletes are tombstones until synced.
- **Sync never runs on its own.** Coming back online with unsynced changes shows a prompt; only "Sync now" (prompt, button or account screen) runs `SyncEngine`: push dirty records in batches of 500, pull every page since the cursor, then apply the pulled changes (parents first) and save the cursor. A failed run changes nothing that the next run can't redo.
- **Conflicts**: last write wins per record by edit time; ties go to the server. See [`docs/sync-protocol.md`](docs/sync-protocol.md).

#### Timeline engineering

- **One origin.** Every x is derived from time relative to the project's first midnight: `x = (t − origin) minutes × pxPerMinute`; pixel → time rounds to whole minutes. Zoom, scale and row height never change task times.
- **Virtualized viewport.** One horizontal scroll position (`TimelineViewportState`) feeds the header and body. Nothing is laid out at full content width (a long project at high zoom exceeds what Compose can measure): the header and grid draw only visible ticks, and bars are clipped to a page-quantized span and placed in the layout phase, so scrolling doesn't recompose rows. Rows are a `LazyColumn` of `[name cell | lane]`.
- **Gestures.** One `pointerInput` per bar decides tap vs drag; the drag type (move / resize start / resize end) is chosen once at pointer-down. Every pointer event recomputes the bar from the gesture-start task plus the total distance, so slow drags don't get stuck by per-event snapping. The edit is saved on release.
- **Snapping.** Sub-day intervals snap to a fixed grid from local midnight; whole days follow local midnights (DST-safe). The dragged edge snaps; limits (15-minute minimum, project end) are rounded inward so a clamped edge stays on the grid.

### Server

- `POST /v1/sync`: one transaction per request with the user's row locked (a user's syncs run one at a time); each push is a Postgres upsert that only replaces an older copy; stale pushes return the winning copy; pulls page by a per-user sequence number. Tombstones drop the deleted content.
- `/v1/auth/*`: enumeration-safe sign-up/forgot (always "check your email"), hashed one-time codes with attempt limits, rate limits per email and IP, Sanctum tokens (60 days, `sync` ability), disabled accounts refused.
- **Admin** (`/admin`, separate `staff` guard): authenticator-app MFA required; roles admin/support; staff management (admins); user search, disable/enable, sign out everywhere, delete (admins, typed confirmation + reason); dashboard (accounts, active users, sign-ups, sync volume, error rate, latency); support data access (reason → 30 minutes, read-only, re-checked on every request). Every privileged action goes to an append-only audit log; user pages never show project content.
- **Container**: one FrankenPHP/PHP 8.4 image, roles `web`, `worker`, `scheduler`, `migrate`; non-root; caches built at start from the injected environment.

## Testing

| Suite | Command | Count |
|---|---|---|
| Android JVM unit tests | `./gradlew :app:testDebugUnitTest` | 122 |
| Server (Pest, real Postgres) | `cd server && composer test` | 103 |
| Server inside the image | `docker compose --profile test run --rm --build test` | 103 |
| Infrastructure assertions | `cd server/infra && npm test` | 13 |

The app's core (time math, snapping, edits, viewport, rules, sync engine with an in-memory server) and the server's sync protocol are covered by unit and feature tests; the sync API passes the conformance scenarios in `docs/sync-protocol.md`. Behaviour on a real phone (Android 10, 360 dp) — gestures, scales, migrations, the reconnect prompt and end-to-end sync against the local server with a second simulated device — and the admin panel in a browser were checked by hand with predicted results; the log is in [`docs/AI_USAGE_LOG.md`](docs/AI_USAGE_LOG.md).

## Decisions and trade-offs

- **Offline first, sync on request.** The user asked to be prompted on reconnect rather than syncing silently; sync never starts on its own.
- **Last write wins per record**, by edit time with the server clock as a ceiling — simple and predictable; no field-level merging.
- **Laravel + Postgres + Filament** instead of the first serverless prototype (Cognito, Lambda, DynamoDB; tag `serverless-backend-v1`): an admin back-office and relational data were wanted. Trade-off: an always-on database and containers, and our own auth to secure.
- **No custom domain required on AWS**: CloudFront's default domain gives HTTPS; the load balancer is private (VPC origin), so client IPs come from CloudFront's own header. A custom domain is needed to enforce TLS 1.2+.
- **Fixed 15-minute minimum duration** across all snap intervals; the dragged edge snaps to the nearest allowed grid line.
- **Pinned dependency versions**, each at least two weeks old when chosen; lint and npm report newer ones.

## Known limitations

- Not deployed: the AWS stack has been synthesized and tested, not run. Email in AWS needs SES production access.
- Dragging a handle past the edge of the viewport doesn't auto-scroll.
- On a 360 dp phone the four scale buttons push the zoom, snap and row-height controls off-screen (the toolbar scrolls sideways).
- Dependency arrows update when a drag ends, not during it.
- In Months scale a project that starts mid-month shows no month label until the next 1st (the corner label shows the date).
- With the keyboard open, the account screen's main button can sit under the keyboard (the keyboard's Done key submits).
- Admin times are shown in UTC (labelled), not converted to the viewer's time zone.
- Light theme only. No instrumented or UI test suite; device behaviour was verified by hand.

## AI usage

Built with Claude Code. [`docs/AI_USAGE_LOG.md`](docs/AI_USAGE_LOG.md) is the per-batch development log (including AI mistakes and how they were found); [`docs/AI_USAGE_WRITEUP.md`](docs/AI_USAGE_WRITEUP.md) is the summary written for the original take-home.
