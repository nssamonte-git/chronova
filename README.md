# Chronova — Shape Your Time

Chronova is an offline-first project scheduler: a native Android app (Kotlin, Jetpack Compose) with a Gantt-style timeline, a Laravel server it syncs with, and a staff admin panel.

- **App** (`Chronova - Android App/`) — create projects with a date range, add tasks and finish-to-start dependencies, and edit them on a timeline by dragging and resizing bars (Hours, Days, Weeks or Months). Everything works offline; when the phone comes back online with unsynced changes it **asks** before syncing.
- **Server** (`server/`) — Laravel 13 on Postgres: sign-up with an emailed code, Sanctum tokens, and a sync API (last write wins per record).
- **Admin** (`/admin`) — Filament 5 panel for staff with 2FA required by default: user management, a health dashboard, an audit log, and time-limited, audited support access to a user's projects. Live at [chronova.meenux.tech](https://chronova.meenux.tech); a shared Support test login is on page 16 of the writeup.
- **AWS** — the server is **live** on one EC2 instance (Docker Compose, Caddy HTTPS, Cloudflare DNS, secrets from Secrets Manager); see [`server/infra/EC2.md`](server/infra/EC2.md). The planned production stack, CDK for CloudFront → private load balancer → ECS Fargate, RDS Postgres and SES (`server/infra/`), is tested but not deployed; see [`server/infra/DEPLOY.md`](server/infra/DEPLOY.md).

It started as the PicCollage take-home (a single-screen timeline). No third-party timeline or Gantt library is used. A short writeup for reviewers (requirements, architecture, every feature beyond the brief, the AI model APIs and why they were chosen, testing, the AWS deployment, a setup guide from GitHub to an APK with troubleshooting, the sign-in, sign-up and demo flows, how the app talks to the server and how that traffic is encrypted, the admin panel with a test login, the security build gate, AI usage and the AI workflow) is [`docs/take-home/chronova-take-home.pdf`](docs/take-home/chronova-take-home.pdf).

**Try it in a few minutes:** clone the repository, open `Chronova - Android App/` in Android Studio and run it, or build the APK (see [Running the app](#running-the-app)). On the sign-in page, **Try demo** opens a shared demo account with a 1,000-task sample project. The writeup's page 2 walks from GitHub to an installed APK, page 3 covers what can go wrong, and page 16 shows the admin panel.

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
- **Duplicate and "Start from"**: any project can be a template. "Duplicate" in a project's menu, or "Start from" in the new-project sheet, copies its tasks and links to a new start date, keeping the length and each task's local time of day (across daylight-saving changes too). The copy starts with nothing done; notes, colours and milestones are kept.
- **Tasks**: add, edit (title, colour, start/end date and time), delete with undo.
- **Task reminders**: on the account page, Off / When a task starts / 15 minutes / 1 hour / 1 day before. A notification (own "Task reminders" channel) comes for every task or milestone that isn't done, in every project; tapping it opens the project. The choice is per phone and off until chosen. The server pushes reminders for synced tasks (Firebase Cloud Messaging) to every signed-in phone, and each phone's own alarm covers changes not synced yet; a reminder is shown once either way.
- **Export**: "Export…" in a project's menu opens a sheet: a PDF Gantt chart (the whole project on landscape A4 pages, always light), a CSV spreadsheet (one row per task, for Excel, Sheets or Numbers) or a calendar file (.ics, every task as an event; milestones are events with no length), with a switch for task notes. The file goes to Android's share sheet; nothing is sent until you pick where.
- **Dark theme**: light by default, with a System (follow the phone) / Light / Dark choice on the account page (kept on this device, not synced). Both themes take their colours from the logo: a near-white page or the logo's navy, its cyan-to-violet gradient on a few small shapes, and the same four task colours in each, so bar labels stay readable. Text is set in Inter (bundled, SIL Open Font License). The status bar follows the app's theme.
- **Search, filters and Today**: the search button opens a bar that matches task titles and filters by colour and by not done / done / milestones (chips of one kind widen, the kinds narrow), with a match count and Clear. While a search or filter is on, every match gets a row and the view scrolls to the earliest one. It lasts only while the project is open. The calendar button jumps to now (or says when today is outside the project), and a line marks now on the timeline.
- **Progress, notes and milestones**: each task has a percent complete (a slider in steps of 10, a Done box, or the ✓ in the top bar for the selected task, with Undo); the done part of a bar is shaded, done tasks get a check, and project cards say how many tasks are done. Free-text notes (up to 2,000 characters) stay on the task: they sync, but never go to the voice providers or the staff admin panel. A milestone is a moment rather than a span (the editor's Milestone switch): it is drawn as a diamond, can be dragged and linked like any task, is never resized, and is either done or not.
- **Scales**: Hours, Days, Weeks, Months; ticks follow the real calendar (DST days, month lengths) and whole-day snapping follows local midnights.
- **Dependencies**: finish-to-start links chosen in the task editor (cycles are blocked); arrows on the timeline turn red when violated. After a drag, resize or edit leaves tasks that wait for a task starting before it ends, a message offers **Push**: they move later, just enough, keeping their lengths, with Undo (a drag itself still moves only its own task).
- **Critical path**: a switch in the timeline settings (remembered per project) outlines in red the tasks where any delay moves the project's finish and fades the rest; the task editor says how long a task can slip without delaying the finish. Slack is counted in working time, a fixed 09:00–17:00, Monday to Friday.
- **Offline-first sync**: local Room database; a reconnect prompt ("You're back online — sync now?"), a sync button with the number of unsynced changes, last-write-wins conflict handling, sign-out/delete account clear the device.
- **Accounts**: the app opens on a sign-in page and needs an account to be used (sign up with an emailed 6-digit code, sign in, forgot/reset password). **Try demo**, above the form, signs into a shared demo account (`test@chronova.com`, already confirmed, with Sample Project: 1,000 tasks) and syncs it straight away; the app hides Delete account for it; signing out or deleting the account returns there, and so does a session the server ends (expired, signed out everywhere, account disabled), with the phone's projects kept for that account and cleared before a different one signs in. With "Remember me" ticked (the default) the session is kept (token encrypted with a Keystore key), so later launches open straight into the app, offline too; unticked, it lasts only while the app runs; each use moves the token's expiry 60 days ahead, so only a device unused for 60 days has to sign in again. Once signed in it works offline as before. The token is encrypted with an Android Keystore key.
- **Schedules from images**: photograph a schedule or Gantt chart, or pick an image of one, to make a new project ("Start from: An image of a chart…" in the new-project sheet) or add its tasks to the open project (the picture button on the timeline). Meta reads the image and OpenAI builds the tasks from what Meta read (never the image); a preview shows the tasks, milestones and links found, and what to check, with rows to untick and a start date for charts without dates; Undo afterwards. Signed in and online; see [`docs/import-api.md`](docs/import-api.md).
- **Notifications**: a bell in the project list (with an unread count) opens a page of what happened on this phone: syncs (sent/received, failures, changes waiting, changes where a newer copy was kept), the assistant's changes (with Undo while the tasks are unchanged, and Open project) and account events (signed in, password changed, session ended, a sign-in on another device). Pushed from the server (Firebase Cloud Messaging): "changes on another device" (tapping it asks whether to sync now; never syncs by itself) and "new sign-in" on another device; a password reset, the account deleted elsewhere or support signing you out everywhere signs this phone out at once (the phone checks with the server first). Filter by type, unread and project; sort newest or oldest first; mark all as read; clear. Failed syncs, ended sessions, changes on another device and new sign-ins also go to the phone's notification shade (asked for on Android 13+), and tapping one opens the page. Kept on the phone for 30 days (up to 200), cleared with the phone's data on sign-out.
- **Voice assistant** (signed in, online): when the app opens on the project list the orb greets you (the very first time with a short introduction); tap the orb just above "Add task" or "New project" and it comes to the centre of the screen and listens; it says hello, and you say what to change ("push build by two hours and everything after it"). It ends your turn by itself after a short silence. The server transcribes (ElevenLabs Scribe) and interprets (OpenAI, strict schema) the request; the app checks the proposal against its own data, the orb says what will change and asks "Shall I apply it?" — or, when the change would overlap another task or start a task before one it waits for ends, names the clash and offers a clash-free alternative that OpenAI suggests and the app re-checks ("Shall I also move Build to 5 to 7 p.m.?"; no → "Shall I apply it as you asked, even with the clash?") — and you answer by voice: yes applies, no leaves everything as it was, anything else is taken as a corrected request. It also understands progress and milestones ("mark design done", "add a milestone Launch on Friday at 5"); a task's notes are never sent. It asks out loud when the request is unclear. It also drives the app (it's on the project list too): "go to Friday", "show me Beta", "switch to weeks", "zoom in", "show the critical path", "which tasks aren't done?", "open Gamma", "open Jnn", "show my notifications", "dark mode", "remind me an hour before each task", "sync now" and "export this as a PDF" run at once and are said aloud. Changes still wait for a yes: "mark this done" (the selected task), "undo that" (its own last change), "new project Patio from Monday to Friday" and "copy Jnn starting next week" (with Undo). Once applied the orb hides and says what changed while the changed task is highlighted on the timeline, and **Undo** follows. There is no text field; tapping outside the orb or Back stops it. "…and everything after it" pushes dependent tasks just enough; dragging never moves other tasks. See [`docs/voice-api.md`](docs/voice-api.md).

## Branding

The Chronova logo (`docs/brand/chronova-logo.webp`) is used as supplied. `python3 tools/brand/generate.py` (Pillow) rebuilds everything from it: the Android adaptive and themed icon layers (`Chronova - Android App/app/src/main/res/drawable-nodpi/brand_icon_*`) and the launch-screen logo, also shown on the sign-in page (`Chronova - Android App/core/designsystem/src/main/res/drawable-nodpi/brand_splash_logo.png`), and the admin panel's logo and favicons (`server/public/images/brand/`, `server/public/favicon.ico`). The admin panel is dark-only in the logo's colours (`AdminPanelProvider`, `server/resources/css/admin-theme.css`).

The assistant orb is the supplied 3D model (`docs/brand/assistant-orb.glb`) in the colours of its reference image (`docs/brand/assistant-orb-reference.webp`). `cd tools/brand/orb && npm ci && node convert.mjs` (glTF-Transform, Draco, sharp) writes the copy the app renders, `Chronova - Android App/feature/assistant/presentation/src/main/assets/orb/assistant_orb.glb` (880 KB, byte-identical on every run): Draco decoded, textures as JPEG/PNG, a glossy material with an inner glow. The app draws it live with Filament, lit by blue, violet and magenta lights and a reflection map in the same colours, with a halo and sparkle drawn by Compose; it falls back to the mic icon if the 3D view can't start.

## Repository layout

One git repository; this folder is its root. The Android app is a self-contained Gradle project in its own folder, and everything else sits next to it.

```
.
├── Chronova - Android App/          the Android app — open this folder (not the repository root) in Android Studio
│   ├── app/                      the shell: application, navigation, manifest, launcher icons; instrumented tests (Room, rotation, pinch)
│   ├── core/                     shared modules: model, timeline, domain, database, network, data, designsystem, ui, testing
│   ├── feature/                  projects, timeline, notifications, sync, account, assistant, export, reminders, push, chartimport — each split into domain / data / presentation
│   ├── build-logic/              Gradle convention plugins, the module-graph and UI-model checks
│   ├── build-validator/          the security build gate (runs before every build; see Testing)
│   ├── gradle/  build.gradle.kts  settings.gradle.kts  gradlew
│   └── local.properties          uncommitted, optional: SDK path (Android Studio writes it), a different server URL
├── server/                       Laravel app: API, sync, voice, admin panel; Dockerfile, compose.yaml
│   ├── app/  routes/  resources/  public/  tests/
│   └── infra/                    AWS CDK (the planned stack) + DEPLOY.md; EC2.md runs the live server
├── docs/
│   ├── sync-protocol.md          the app ↔ server sync contract
│   ├── voice-api.md              voice endpoints, privacy, measured cost and the release checklist
│   ├── push.md                   push notifications: devices, reminders, alerts, settings
│   ├── import-api.md             schedules from images: contract, how it's read, measured results, privacy, release checklist
│   ├── brand/                    the logo as supplied
│   ├── AI_USAGE_LOG.md           per-batch development log
│   ├── AI_USAGE_WRITEUP.md       AI usage summary: the take-home and everything after it
│   └── take-home/                the take-home writeup as a PDF, with its HTML source
├── tools/
│   ├── check.sh                  the full app check (CI and the pre-push hook run it)
│   ├── git-hooks/                pre-push hook
│   ├── qa/                       rotation stress test on a phone
│   └── brand/                    icon and logo generator (writes into both the app and the server); orb/ converts the 3D orb for the app
├── .github/workflows/android.yml CI: tools/check.sh, then the device tests on an emulator
│
│   the project AI kit (how AI sessions work on this repository; see "AI usage")
├── AGENTS.md  HANDOFF.md         startup rules, then layout, guardrails, invariants and commands
├── manifest.yaml                 routes each kind of task to the files it needs
├── CURRENT_TASK.md  DECISIONS.md where the work stands; dated design decisions
├── PROJECT_CONTEXT.md            a stable profile of the project (read on demand)
├── PROJECT_RELATIONSHIP_MAP.md   what loads, validates or documents what (data in project-relationships.tsv)
├── Learn/                        project map, architecture, workflow, validation
├── scripts/                      context and map validation; lesson capture, sanitizing and promotion
├── md-templates/                 the lesson template
└── learning-inbox/               lessons waiting for review (only the README is in git)
```

App commands run from `Chronova - Android App/`; server and infrastructure commands from `server/` and `server/infra/`; git and the brand generator from the repository root.

## Running the app

Requirements: Android Studio with AGP 9.5 support (Gradle 9.6 comes with the wrapper), Android SDK 37; `minSdk` 26. Gradle runs itself on **JDK 25** (`gradle/gradle-daemon-jvm.properties`) and downloads it on the first build when the computer doesn't have it; any JDK 17+ can start the wrapper from a terminal, and Android Studio handles it.

Special steps, beyond opening the project:

- **Internet on the first build:** Gradle, JDK 25 (from foojay.io, unless JDK 25 is installed), the dependencies, and the live server for signing in. Behind a proxy, set it in Android Studio (Settings → System Settings → HTTP Proxy).
- **Windows:** install [Git for Windows](https://git-scm.com/download/win). Every build first runs the security gate, a shell script, through Git's `bash.exe`; the build stops with a message saying so if it's missing. macOS and Linux need nothing extra.
- **Release APK:** sign it with your own key (Build → Generate Signed App Bundle or APK); no signing keys are in the repository. Debug builds need nothing.

A fresh clone (`git clone https://github.com/nssamonte-git/chronova.git`, or Code → Download ZIP on GitHub) builds as it is: open `Chronova - Android App/` in Android Studio (it finds the SDK itself), or use Gradle from that folder with `ANDROID_HOME` pointing at the SDK:

```bash
cd "Chronova - Android App"
./gradlew :app:installDebug   # build and install on a phone or emulator
./gradlew :app:assembleDebug  # just the APK: app/build/outputs/apk/debug/app-debug.apk (Windows: gradlew.bat)
../tools/check.sh             # everything: tests, lint, architecture checks, screenshots, security gate
```

Install a built APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. `.gitattributes` keeps `gradlew` and the shell scripts' line endings right on Windows; on Windows, run `tools/check.sh` in Git Bash or WSL. If a build, install or sign-in fails (SDK location, Java version, a proxy, `INSTALL_FAILED_UPDATE_INCOMPATIBLE` from another computer's debug key, "Too many attempts"), page 3 of the writeup lists the causes and fixes.

The app connects to the live server, `https://chronova.meenux.tech` ([`server/infra/EC2.md`](server/infra/EC2.md)), with no setup. On the sign-in page, **Try demo** opens a shared demo account with a 1,000-task sample project. To use another server, set it in the uncommitted `Chronova - Android App/local.properties`:

```properties
chronova.apiUrl=https://<your server>          # release builds need https
# or, for a debug build against the local server (server/ with php artisan serve):
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
composer test                 # Pest (528 tests)
```

Push notifications use a fake sender by default (recorded, not sent). To send through Firebase, put the service-account key file at `server/storage/credentials/firebase-service-account.json` (git-ignored) and set `PUSH_SENDER=fcm` and `FCM_CREDENTIALS=<its absolute path>` in `server/.env`; reminders also need `php artisan schedule:work` (see `docs/push.md`).

Voice uses fakes by default (no keys, no network: a fixed transcript, a rule-based interpreter, a plain summary and a recorded clip). For the real providers, put `OPENAI_API_KEY` and `ELEVENLABS_API_KEY` in `server/.env` and set `VOICE_TRANSCRIBER=elevenlabs`, `VOICE_INTERPRETER=openai`, `VOICE_EXPLAINER=openai`, `VOICE_SPEECH=elevenlabs` (see `docs/voice-api.md`).

## Architecture

### App

Gradle modules in three layers, with manual dependency injection in the shell (`ChronovaApp` / `AppContainer`). Dependencies point inward — `presentation → domain ← data` — features never depend on each other, and core never depends on features; `./gradlew verifyModuleGraph` checks this. Domain modules are plain Kotlin (no Android plugin), and every ViewModel action goes through a use case.

```
:app                          shell: application + AppContainer, activity, navigation (sign-in gate, routes), backend config
:core:model                   Project, SchedulerTask, TaskDependency, TimelineConfig            (pure Kotlin)
:core:timeline                time ↔ pixels, calendar ticks, snap grids, edits, hit testing, viewport math   (pure Kotlin)
:core:domain                  repository and auth/sync contracts, rules, ScheduleChange, ApplyScheduleChanges (one transaction, stale-checked, undoable)   (pure Kotlin)
:core:database                Room: projects, tasks, dependencies, view_settings, notifications (schema v6, with migrations)
:core:data / :core:network    RoomProjectRepository (every write stamps updatedAt and marks the row dirty), connectivity; HTTP client and JSON
:core:designsystem / :core:ui theme and shared images; time formatting, sync status UI, the assistant slot the timeline hosts
:feature:projects             domain (list/save/delete use cases), presentation (project list and editor)
:feature:timeline             domain (schedule, edit, task editor, undo, view settings), presentation (timeline screen, task editor, components)
:feature:notifications        domain, data (Room), presentation (page, wording, the notification shade)
:feature:sync                 domain, data (SyncEngine, RoomLocalSyncStore, SyncCoordinator with the reconnect prompt, sync client), presentation
:feature:account              domain, data (ApiAuthRepository, Keystore token store), presentation (sign-in and account pages)
:feature:assistant            domain (AICommandHandler, CommandPlanner, clashes, use cases), data (voice client, microphone, player), presentation (ViewModel, 3D orb with Filament)
:feature:export               domain (CSV and calendar files, ExportProject), data (PDF Gantt chart, shared files), presentation (export sheet)
:feature:reminders            domain (ReminderPlanner, ReminderEngine), data (the alarm, notifications, receivers)
:feature:push                 domain (register the device, handle alerts), data (Firebase messaging service, push API client)
:feature:chartimport          domain (plan the import), data (image API client, chart images), presentation (import sheet and preview)
:core:testing                 shared test helpers
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
- **No public website**: the site root (`/`) redirects to `/admin`; the app talks only to `/v1`, and `/up` is the health check.
- **Admin** (`/admin`, separate `staff` guard): authenticator-app 2FA required by default; roles admin/support; staff management (admins), including **Disable 2FA** and **Require 2FA** for other staff; user search, disable/enable, sign out everywhere, delete (admins, typed confirmation + reason); dashboard (accounts, active users, sign-ups, sync volume, error rate, latency); support data access (reason → 30 minutes, read-only, re-checked on every request). Every privileged action goes to an append-only audit log; user pages never show project content.
- **Voice** (`/v1/voice/transcribe`, `/interpret`, `/explain`): provider keys only on the server; the model's answer is constrained by a strict JSON schema and re-validated (known operation types, ids from the request, real dates); the user's words and task titles are sent as data, never as instructions; OpenAI with `store: false`; no audio or words stored or logged — `voice_events` keeps counts, timing and token use for the dashboard. Kill switch, per-user/per-endpoint rate limits, fakes for development and tests.
- **Container**: one FrankenPHP/PHP 8.4 image, roles `web`, `worker`, `scheduler`, `migrate`; non-root; caches built at start from the injected environment.

#### AI models

The voice assistant and schedules from images call these through the server, which holds the keys (models set in `server/config/voice.php` and `server/config/import.php`):

| Provider · model | Used for | Why |
|---|---|---|
| ElevenLabs Scribe v2 | Speech to text (the request and the spoken yes or no) | Can be given the project's task titles in advance, so unusual task names are spelled as written; about 1 s per request |
| ElevenLabs Eleven v4 Turbo | The assistant's spoken answers | ElevenLabs' real-time model (about the latency of Flash v2.5, used until 15 Nov 2026) that also acts on audio tags: every line is said `[excited, cheerful]`, for an energetic, upbeat voice; about twice Flash's price per character |
| ElevenLabs voice isolator (off by default) | Optional noise removal before transcription | Same provider, so the audio goes to no one new |
| OpenAI GPT-6.1 Terra, reasoning "low" | Words → schedule changes, clash-free alternatives, the sentences the orb speaks, tasks from an image's reading | A strict JSON schema limits it to allowed operations on the tasks it was given, and the server and app re-check every answer; 15 of 15 sample requests right; its fixed rules are prompt-cached |
| Meta Muse Spark 1.3, reasoning "minimal" | Reading a chart image: names, dates on the axis, rows | Reads a chart the way a person does, though not exact positions; "minimal" was as accurate as the default in about half the time; standard models, which Meta doesn't train on |
| Meta SAM 3.1 | Exact boxes of bars and milestone diamonds | Exact positions but no text, so it pairs with Muse Spark: SAM gives where, Muse Spark what |

The voice providers were chosen in the feature brief. Details, privacy and measured cost: [`docs/voice-api.md`](docs/voice-api.md), [`docs/import-api.md`](docs/import-api.md).

## Testing

One command checks the whole app, and the pre-push hook and CI (`.github/workflows/android.yml`) run the same one:

```bash
tools/check.sh                # from the repository root
tools/check.sh --devices      # also every module's device tests on a managed emulator
```

| Suite | Command | Count |
|---|---|---|
| Android JVM unit tests (JUnit 4, coroutines-test, OkHttp MockWebServer; 71 files in 31 modules) | `cd "Chronova - Android App" && ./gradlew test` (part of `tools/check.sh`) | 523 |
| Screenshot tests (Compose Preview Screenshot Testing; 61 previews in 10 modules, each at phone size, 1.5× text and dark) | `./gradlew testDebugScreenshotTestDefaultTestSuite`; after an intended visual change, `updateDebugScreenshotTestDefaultTestSuite`, look at the PNGs, commit them | 183 |
| Lint and architecture | `./gradlew :app:lintDebug verifyModuleGraph verifyUiModels` | 0 lint errors |
| Security build gate | runs as `:app:validateThreats` before every build; alone: `sh build-validator/validate-build.sh` (see below) | 89 rules |
| Device tests on a managed emulator (Pixel 6, API 35): Compose UI tests of the screens, Room migrations and transactions, the PDF writer, rotation (Compose UI test, UiAutomator; 18 files in 13 modules) | `tools/check.sh --devices` (nothing is installed on a phone; needs about 8 GB of free disk) | 115 |
| App device tests on a phone (Room transaction, rotation, pinch) | build both APKs, then `tools/qa/rotation-stress.sh instrumented` (installs them over the app, backs its data up and restores it). Never `connectedAndroidTest`, which uninstalls the app and its data | 11 |
| Rotation stress on a phone (adb) | `tools/qa/rotation-stress.sh all` (or one scenario; `list` names them): turns the phone between portrait and landscape in 16 situations and checks for crashes, leaked activities, memory growth and lost state; backs the app's data up and restores it | 16 scenarios |
| Server (Pest, real Postgres) | `cd server && composer test && composer lint` | 528 |
| Server inside the production image | `cd server && docker compose --profile test run --rm --build test` | same suite (last run in the image at 227) |
| Infrastructure assertions | `cd server/infra && npx tsc --noEmit && npm test && npx cdk synth -c mailFrom=no-reply@example.com` | 23 |

**Security build gate.** `Chronova - Android App/build-validator/` is a copy of the canonical mobile build validator. Every Gradle build of the app runs it first: it scans the manifest, Gradle files, Kotlin and Java, `res/` and the repository with 89 rules drawn from 41 threat cards (exported components, WebView and TLS mistakes, weak crypto, secrets in the source, unobfuscated releases…). A critical or high finding fails the build with the fix; medium and low are listed as warnings. A finding can be accepted only with a written reason (an inline `build-validator:ignore` comment or `.build-validator-ignore`), and accepted findings still show in every report. Never lower its threshold or remove the hook.

The app's core (time math, snapping, edits, viewport, rules, sync engine with an in-memory server) and the server's sync protocol are covered by unit and feature tests; the sync API passes the conformance scenarios in `docs/sync-protocol.md`. Behaviour on a real phone (Android 10, 360 dp) — gestures, scales, migrations, the reconnect prompt and end-to-end sync against the local server with a second simulated device — and the admin panel in a browser were checked by hand with predicted results; the log is in [`docs/AI_USAGE_LOG.md`](docs/AI_USAGE_LOG.md).

## Decisions and trade-offs

- **Offline first, sync on request.** The user asked to be prompted on reconnect rather than syncing silently; sync never starts on its own.
- **Last write wins per record**, by edit time with the server clock as a ceiling — simple and predictable; no field-level merging.
- **Laravel + Postgres + Filament** instead of the first serverless prototype (Cognito, Lambda, DynamoDB; tag `serverless-backend-v1`): an admin back-office and relational data were wanted. Trade-off: an always-on database and containers, and our own auth to secure.
- **Live on one EC2 instance first.** The running server is Docker Compose on a single EC2 instance, with PostgreSQL on the same machine, Caddy for HTTPS and the secrets in Secrets Manager; the Fargate and RDS design below stays as the separate CDK stack.
- **No custom domain required for the CDK stack**: CloudFront's default domain gives HTTPS; the load balancer is private (VPC origin), so client IPs come from CloudFront's own header. A custom domain is needed to enforce TLS 1.2+.
- **Fixed 15-minute minimum duration** across all snap intervals; the dragged edge snaps to the nearest allowed grid line.
- **Pinned dependency versions**, each at least two weeks old when chosen; lint and npm report newer ones.
- **Voice proposes, the user decides — by voice.** The assistant is voice-only: it says every change before making it and applies it only on a spoken yes; Undo follows. Silence, a no or tapping away changes nothing. Voice is on for every signed-in user, so cost is bounded by rate limits, a kill switch and the dashboard rather than an opt-in.
- **The assistant learns per user, the model only proposes.** Each user's requests and answers are kept 90 days and sent back with their own next requests as history (deleted with the account, never shown to staff). Clash-free alternatives come from the model but are re-planned and re-checked by the app, with its own fix as the fallback. The interpreter's fixed rules are laid out for OpenAI's prompt cache (most of its input read at 5% of the price); no user content is cached.

## Known limitations

- The live server is a single EC2 instance with PostgreSQL on the same machine (no load balancer, managed database or Multi-AZ). Still open there: real email (mail is held in memory, so sign-up and reset codes aren't sent), voice and push checked end to end from phones, and a tested database restore; image import is off. The CDK stack has been synthesized and tested, not deployed; email through it needs SES production access.
- The demo account and the admin panel's Support test login are shared and public: anyone can change the demo's projects, and the Support login can see every user's email and sync history, disable or sign out accounts and open a user's projects with an audited reason (DECISIONS, 2026-11-15). Re-seed the demo or change that password to reset or withdraw them.
- The live server doesn't report which commit it runs; [`server/infra/EC2.md`](server/infra/EC2.md) ("Check that EC2 runs the latest code") compares its checkout with GitHub.
- The resting assistant orb still redraws the screen about 12 times a second (it's drawn into a TextureView, so each orb frame redraws the window); listening, thinking and speaking run at the full frame rate. On a budget phone (OPPO A15, PowerVR GE8320, debug build, 15 Nov 2026) an idle screen with the orb draws about 10 frames a second, a screen without it none; scrolling the 1,000-task timeline has a median frame of 14 ms but a long tail (99th percentile 150 ms), mostly on the main thread of a debug build.
- Dragging a handle past the edge of the viewport doesn't auto-scroll.
- On a 360 dp phone the four scale buttons push the zoom, snap and row-height controls off-screen (the toolbar scrolls sideways).
- Dependency arrows update when a drag ends, not during it.
- In Months scale a project that starts mid-month shows no month label until the next 1st (the corner label shows the date).
- With the keyboard open, the account screen's main button can sit under the keyboard (the keyboard's Done key submits).
- Admin timestamps, dashboard days, and daily housekeeping use Philippine time (`Asia/Manila`). Database timestamps are stored in UTC.
- Task reminders use an inexact alarm (no special permission), so Android may deliver one a few minutes late while the phone is idle; a reminder more than a day late is dropped. "1 day before" is 24 hours, so across a daylight-saving change it is an hour off the same clock time. A reminder whose time has passed when its task is added or moved is not sent late. A pushed reminder can come a little late too: the server checks once a minute, and Android or the phone maker's battery saving can hold a message back.
- The app's in-app theme choice (System / Light / Dark) applies once Compose draws: the launch screen and the very first frame are light (the default), so with Dark chosen the first frame is briefly light. The admin panel is dark-only.
- While the assistant is open, or still saying what it changed, the screen keeps its orientation; it turns with the phone again once the assistant is done.
- The assistant is voice-only and its yes/no words are English. Turns end on volume (a short silence after speech), so steady talk nearby keeps it listening until the speaker pauses, and nearby voices can be transcribed with the request (it then asks or refuses; nothing is applied without a yes). A yes quieter than someone talking to the phone gets "Sorry, was that a yes?" first; the level is tuned on one phone.
- The 3D orb (Filament 1.79) adds native libraries: the debug APK is about 40 MB (15 MB before); Play's per-device delivery ships one CPU type, about 6 MB more. It needs OpenGL ES 3.0, and falls back to the mic icon otherwise, after the 3D view has failed once, or on a software renderer (SwiftShader: an emulator without a GPU, where Filament crashed mid-render).
- Reading an image takes about 15–20 seconds and needs a connection. It was measured on clean charts, a tilted copy and a table; real camera photos with glare or curved paper, handwriting, and very large charts are untested. Task colours aren't taken from the chart (Chronova has four); notes and owners aren't imported.
- Pushes are Firebase data messages: a phone where the app was force-stopped gets none until the app is opened again, and Android's or the phone maker's battery saving (ColorOS, for one) can hold them back. The phone's own reminder alarm still works. The privacy policy and Play Data safety form must name Firebase Cloud Messaging before release (reminders carry task titles and project names; notes never leave the phone).
- Voice needs a connection and an account; it sends recordings and task names to ElevenLabs and words plus task names/times to OpenAI (the privacy policy and Play Data safety form must say so before release). Task names are passed to the transcriber as words to listen for, which fixes most sound-alike mistakes; anything still ambiguous gets a question.

## AI usage

Built with Claude Code. A short take-home writeup (requirements, architecture, the voice flow, every feature beyond the brief, the AI model APIs and why they were chosen, testing, the AWS deployment, a setup guide from GitHub to an APK with troubleshooting, the sign-in, sign-up and demo flows, how the app talks to the server and how that traffic is encrypted, the admin panel with a test login, the security build gate, AI usage and the AI workflow) is [`docs/take-home/chronova-take-home.pdf`](docs/take-home/chronova-take-home.pdf). [`docs/AI_USAGE_LOG.md`](docs/AI_USAGE_LOG.md) is the per-batch development log (including AI mistakes and how they were found); [`docs/AI_USAGE_WRITEUP.md`](docs/AI_USAGE_WRITEUP.md) summarises it: the take-home as submitted, then the work after it (phases, my decisions, AI mistakes, assessment and timeline).

Every AI session starts from the project AI kit at the repository root: `AGENTS.md`, then `HANDOFF.md`, then only the files `manifest.yaml` routes the task to (`scripts/validate-ai-context.sh` keeps that startup under its token budget). The kit was set up from my canonical AI Knowledge and Skills repository; the build gate in `Chronova - Android App/build-validator/` is copied from it, with its source recorded in `INSTALLED.md`. After a batch whose log entry records an AI mistake or guidance gap that applies beyond Chronova, a sanitized lesson goes to `learning-inbox/` (`scripts/capture-learning.sh`, then `scripts/sanitize-learning-log.sh`); `scripts/promote-learning-candidates.sh` only reports candidates, and I decide what becomes a decision, a Learn page or a proposal to the canonical repository. The steps are in `Learn/03-development-workflow.md`.
