# Chronova — Shape Your Time

Chronova is an offline-first project scheduler: a native Android app (Kotlin, Jetpack Compose) with a Gantt-style timeline, a Laravel server it syncs with, and a staff admin panel.

- **App** (`Chronova - Android App/`) — create projects with a date range, add tasks and finish-to-start dependencies, and edit them on a timeline by dragging and resizing bars (Hours, Days, Weeks or Months). Everything works offline; when the phone comes back online with unsynced changes it **asks** before syncing.
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
- **Voice assistant** (signed in, online): tap the orb in the timeline's top bar and it comes to the centre of the screen and listens; it says hello, and you say what to change ("push build by two hours and everything after it"). It ends your turn by itself after a short silence. The server transcribes (ElevenLabs Scribe) and interprets (OpenAI, strict schema) the request; the app checks the proposal against its own data, the orb says what will change and asks "Shall I apply it?" — or, when the change would overlap another task or start a task before one it waits for ends, names the clash and asks "Shall I apply it anyway?" — and you answer by voice: yes applies, no leaves everything as it was, anything else is taken as a corrected request. It asks out loud when the request is unclear. Once applied the orb hides and says what changed while the changed task is highlighted on the timeline, and **Undo** follows. There is no text field; tapping outside the orb or Back stops it. "…and everything after it" pushes dependent tasks just enough; dragging never moves other tasks. See [`docs/voice-api.md`](docs/voice-api.md).

## Branding

The Chronova logo (`docs/brand/chronova-logo.webp`) is used as supplied. `python3 tools/brand/generate.py` (Pillow) rebuilds everything from it: the Android adaptive and themed icon layers and the launch-screen logo (`Chronova - Android App/app/src/main/res/drawable-nodpi/brand_*`), and the admin panel's logo and favicons (`server/public/images/brand/`, `server/public/favicon.ico`). The admin panel is dark-only in the logo's colours (`AdminPanelProvider`, `server/resources/css/admin-theme.css`).

The assistant orb is the supplied 3D model (`docs/brand/assistant-orb.glb`) in the colours of its reference image (`docs/brand/assistant-orb-reference.webp`). `cd tools/brand/orb && npm ci && node convert.mjs` (glTF-Transform, Draco, sharp) writes the copy the app renders, `Chronova - Android App/app/src/main/assets/orb/assistant_orb.glb` (880 KB, byte-identical on every run): Draco decoded, textures as JPEG/PNG, a glossy material with an inner glow. The app draws it live with Filament, lit by blue, violet and magenta lights and a reflection map in the same colours, with a halo and sparkle drawn by Compose; it falls back to the mic icon if the 3D view can't start.

## Repository layout

One git repository; this folder is its root. The Android app is a self-contained Gradle project in its own folder, and everything else sits next to it.

```
.
├── Chronova - Android App/          the Android app — open this folder (not the repository root) in Android Studio
│   ├── app/src/main/             app code (package noel.chronova.app) and resources
│   ├── app/src/test/             JVM unit tests
│   ├── app/src/androidTest/      instrumented tests (Room)
│   ├── gradle/  build.gradle.kts  settings.gradle.kts  gradlew
│   └── local.properties          uncommitted: SDK path and the server URL
├── server/                       Laravel app: API, sync, voice, admin panel; Dockerfile, compose.yaml
│   ├── app/  routes/  resources/  public/  tests/
│   └── infra/                    AWS CDK + DEPLOY.md
├── docs/
│   ├── sync-protocol.md          the app ↔ server sync contract
│   ├── voice-api.md              voice endpoints, privacy, measured cost and the release checklist
│   ├── brand/                    the logo as supplied
│   ├── AI_USAGE_LOG.md           per-batch development log
│   └── AI_USAGE_WRITEUP.md       AI usage summary: the take-home and everything after it
└── tools/brand/                  icon and logo generator (writes into both the app and the server); orb/ converts the 3D orb for the app
```

App commands run from `Chronova - Android App/`; server and infrastructure commands from `server/` and `server/infra/`; git and the brand generator from the repository root.

## Running the app

Requirements: Android Studio with AGP 9.5 support, JDK 17+, Android SDK 37. `minSdk` 26. Open `Chronova - Android App/` in Android Studio, or use Gradle from that folder:

```bash
cd "Chronova - Android App"
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Without a server the app works fully offline and the account screen says sync isn't set up. To sync, set the server URL in the uncommitted `Chronova - Android App/local.properties`:

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
composer test                 # Pest (261 tests)
```

Voice uses fakes by default (no keys, no network: a fixed transcript, a rule-based interpreter, a plain summary and a recorded clip). For the real providers, put `OPENAI_API_KEY` and `ELEVENLABS_API_KEY` in `server/.env` and set `VOICE_TRANSCRIBER=elevenlabs`, `VOICE_INTERPRETER=openai`, `VOICE_EXPLAINER=openai`, `VOICE_SPEECH=elevenlabs` (see `docs/voice-api.md`).

## Architecture

### App

In `Chronova - Android App/app/src/main/java/`: a single module with package boundaries and manual dependency injection (`ChronovaApp` / `AppContainer`):

```
noel.chronova.app
├── core/timeline     pure Kotlin: time ↔ pixels, calendar ticks, snap grids, edits, hit testing, viewport math
├── domain            models (Project, SchedulerTask, TaskDependency, TimelineConfig), rules (projects, tasks, dependencies)
├── data/local        Room: projects, tasks, dependencies, view_settings (schema v3, with migrations)
├── data/repository   RoomProjectRepository (every write stamps updatedAt and marks the row dirty)
├── data/remote       OkHttp sync and voice clients + DTOs
├── data/sync         SyncEngine, RoomLocalSyncStore, SyncCoordinator (reconnect prompt), connectivity
├── domain/schedule   ScheduleChange, ApplyScheduleChanges (one transaction, stale-checked, undoable), CommandPlanner, DependentsRescheduler
├── domain/voice      VoiceContext, AICommandHandler (proposal → commands → plan), PreviewItem
├── voice             microphone recorder, end-of-speech detection, speech player, 3D orb (Filament)
├── auth              ApiAuthRepository, Keystore token store, backend config
├── navigation        project list → timeline, account
└── presentation      projects, timeline (+ task editor), account, sync UI, components
```

- **Local first.** Screens read Room through repository `Flow`s; edits are written locally and marked dirty. Deletes are tombstones until synced.
- **Sync never runs on its own.** Coming back online with unsynced changes shows a prompt; only "Sync now" (prompt, button or account screen) runs `SyncEngine`: push dirty records in batches of 500, pull every page since the cursor, then apply the pulled changes (parents first) and save the cursor. A failed run changes nothing that the next run can't redo.
- **Conflicts**: last write wins per record by edit time; ties go to the server. See [`docs/sync-protocol.md`](docs/sync-protocol.md).
- **One way to change the schedule.** Drags, the task editor, delete/undo and the assistant all write through `ApplyScheduleChanges`: one Room transaction that refuses changes computed from a copy that has changed since (an edit or a sync in between) and returns the inverse for Undo. The assistant's proposal is planned against the schedule *as it is now*, previewed, and re-checked on Apply.

#### Timeline engineering

- **One origin.** Every x is derived from time relative to the project's first midnight: `x = (t − origin) minutes × pxPerMinute`; pixel → time rounds to whole minutes. Zoom, scale and row height never change task times.
- **Virtualized viewport.** One horizontal scroll position (`TimelineViewportState`) feeds the header and body. Nothing is laid out at full content width (a long project at high zoom exceeds what Compose can measure): the header and grid draw only visible ticks, and bars are clipped to a page-quantized span and placed in the layout phase, so scrolling doesn't recompose rows. Rows are a `LazyColumn` of `[name cell | lane]`.
- **Gestures.** One `pointerInput` per bar decides tap vs drag; the drag type (move / resize start / resize end) is chosen once at pointer-down. Every pointer event recomputes the bar from the gesture-start task plus the total distance, so slow drags don't get stuck by per-event snapping. The edit is saved on release.
- **Snapping.** Sub-day intervals snap to a fixed grid from local midnight; whole days follow local midnights (DST-safe). The dragged edge snaps; limits (15-minute minimum, project end) are rounded inward so a clamped edge stays on the grid.

### Server

In `server/` (Laravel 13; `server/README.md` covers setup and commands):

- `POST /v1/sync`: one transaction per request with the user's row locked (a user's syncs run one at a time); each push is a Postgres upsert that only replaces an older copy; stale pushes return the winning copy; pulls page by a per-user sequence number. Tombstones drop the deleted content.
- `/v1/auth/*`: enumeration-safe sign-up/forgot (always "check your email"), hashed one-time codes with attempt limits, rate limits per email and IP, Sanctum tokens (60 days, `sync` ability), disabled accounts refused.
- **Admin** (`/admin`, separate `staff` guard): authenticator-app MFA required; roles admin/support; staff management (admins); user search, disable/enable, sign out everywhere, delete (admins, typed confirmation + reason); dashboard (accounts, active users, sign-ups, sync volume, error rate, latency); support data access (reason → 30 minutes, read-only, re-checked on every request). Every privileged action goes to an append-only audit log; user pages never show project content.
- **Voice** (`/v1/voice/transcribe`, `/interpret`, `/explain`): provider keys only on the server; the model's answer is constrained by a strict JSON schema and re-validated (known operation types, ids from the request, real dates); the user's words and task titles are sent as data, never as instructions; OpenAI with `store: false`; no audio or words stored or logged — `voice_events` keeps counts, timing and token use for the dashboard. Kill switch, per-user/per-endpoint rate limits, fakes for development and tests.
- **Container**: one FrankenPHP/PHP 8.4 image, roles `web`, `worker`, `scheduler`, `migrate`; non-root; caches built at start from the injected environment.

## Testing

| Suite | Command | Count |
|---|---|---|
| Android JVM unit tests | `cd "Chronova - Android App" && ./gradlew :app:testDebugUnitTest` | 210 |
| Android instrumented (Room transaction) | from `Chronova - Android App/`: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`, `adb install -r` the APK in `app/build/outputs/apk/debug/` and `adb install -r -t` the one in `app/build/outputs/apk/androidTest/debug/`, then `adb shell am instrument -w noel.chronova.app.test/androidx.test.runner.AndroidJUnitRunner` (not `connectedAndroidTest`, which uninstalls the app and its data) | 3 |
| Server (Pest, real Postgres) | `cd server && composer test` | 261 |
| Server inside the image | `cd server && docker compose --profile test run --rm --build test` | same suite (last run in the image at 227) |
| Infrastructure assertions | `cd server/infra && npm test` | 16 |

The app's core (time math, snapping, edits, viewport, rules, sync engine with an in-memory server) and the server's sync protocol are covered by unit and feature tests; the sync API passes the conformance scenarios in `docs/sync-protocol.md`. Behaviour on a real phone (Android 10, 360 dp) — gestures, scales, migrations, the reconnect prompt and end-to-end sync against the local server with a second simulated device — and the admin panel in a browser were checked by hand with predicted results; the log is in [`docs/AI_USAGE_LOG.md`](docs/AI_USAGE_LOG.md).

## Decisions and trade-offs

- **Offline first, sync on request.** The user asked to be prompted on reconnect rather than syncing silently; sync never starts on its own.
- **Last write wins per record**, by edit time with the server clock as a ceiling — simple and predictable; no field-level merging.
- **Laravel + Postgres + Filament** instead of the first serverless prototype (Cognito, Lambda, DynamoDB; tag `serverless-backend-v1`): an admin back-office and relational data were wanted. Trade-off: an always-on database and containers, and our own auth to secure.
- **No custom domain required on AWS**: CloudFront's default domain gives HTTPS; the load balancer is private (VPC origin), so client IPs come from CloudFront's own header. A custom domain is needed to enforce TLS 1.2+.
- **Fixed 15-minute minimum duration** across all snap intervals; the dragged edge snaps to the nearest allowed grid line.
- **Pinned dependency versions**, each at least two weeks old when chosen; lint and npm report newer ones.
- **Voice proposes, the user decides — by voice.** The assistant is voice-only: it says every change before making it and applies it only on a spoken yes; Undo follows. Silence, a no or tapping away changes nothing. Voice is on for every signed-in user, so cost is bounded by rate limits, a kill switch and the dashboard rather than an opt-in.

## Known limitations

- Not deployed: the AWS stack has been synthesized and tested, not run. Email in AWS needs SES production access.
- Dragging a handle past the edge of the viewport doesn't auto-scroll.
- On a 360 dp phone the four scale buttons push the zoom, snap and row-height controls off-screen (the toolbar scrolls sideways).
- Dependency arrows update when a drag ends, not during it.
- In Months scale a project that starts mid-month shows no month label until the next 1st (the corner label shows the date).
- With the keyboard open, the account screen's main button can sit under the keyboard (the keyboard's Done key submits).
- Admin times are shown in UTC (labelled), not converted to the viewer's time zone.
- The app is light-themed (the admin panel is dark-only). Only three instrumented tests (the Room transaction) and no UI test suite; device behaviour was verified by hand.
- The assistant is voice-only and its yes/no words are English. Turns end on volume (a short silence after speech), so steady talk nearby keeps it listening until the speaker pauses, and nearby voices can be transcribed with the request (it then asks or refuses; nothing is applied without a yes).
- The 3D orb (Filament 1.79) adds native libraries: the debug APK is about 40 MB (15 MB before); Play's per-device delivery ships one CPU type, about 6 MB more. It needs OpenGL ES 3.0, and falls back to the mic icon otherwise or after the 3D view has failed once.
- Voice needs a connection and an account; it sends recordings and task names to ElevenLabs and words plus task names/times to OpenAI (the privacy policy and Play Data safety form must say so before release). Task names are passed to the transcriber as words to listen for, which fixes most sound-alike mistakes; anything still ambiguous gets a question.

## AI usage

Built with Claude Code. [`docs/AI_USAGE_LOG.md`](docs/AI_USAGE_LOG.md) is the per-batch development log (including AI mistakes and how they were found); [`docs/AI_USAGE_WRITEUP.md`](docs/AI_USAGE_WRITEUP.md) summarises it: the take-home as submitted, then the work after it (phases, my decisions, AI mistakes, assessment and timeline).
