# Decisions

Active durable decisions only. Full rationale is in `README.md` ("Decisions and trade-offs") and `docs/AI_USAGE_LOG.md`.

## 2026-11-11: Single scroll authority

- Decision: one horizontal scroll position in `TimelineViewportState` (2026-11-11 revision for multi-project ranges; previously a `horizontalScroll` `ScrollState`). Header and body feed it via `scrollable(reverseDirection = true)`; header, grid and bars read it in draw/layout. Bars are clipped to a page-quantized compose span; rows are a `LazyColumn`.
- Reason: no drift between header and body, no recomposition per scroll frame, and user-defined project ranges can be far wider than a layout can measure.
- Constraint: never measure timeline content at full width; a bar container wider than its lane needs `wrapContentWidth(unbounded = true)` or `width` gets clamped.
- Revised 2026-11-13 (user choices): while a search or filter is on, the list is only the matches and every match gets a row wherever it is in time, and the view scrolls to the earliest; search and filters live in the ViewModel's memory only and end with the screen.
- Revised 2026-11-12 (user choice): rows list only the tasks whose time overlaps what is on screen (`TaskVisibility`), updating as you scroll, plus the selected task (a drag selects its bar, so it never loses its row) and any the assistant just highlighted; with none on screen the timeline says "No tasks here". The list is a `derivedStateOf`, so rows recompose only when it changes, not per scroll frame.

## 2026-11-11: Gesture model

- Decision: dragging a bar moves it directly. Empty space and the header scroll. Resize handles appear only on the selected bar (20 dp outside + up to 20 dp inside each edge; the middle third always moves). Dragging never moves other tasks.
- Reason: a user choice (direct drag over long-press). It also prevents accidental resizes.
- Constraint: one detector per bar. The mode is chosen at pointer-down. The preview is local and committed on release.

## 2026-11-11: Time model and snapping

- Decision: epoch-millis timestamps; minute-resolution pixel → time; `floorDiv` nearest snapping from local midnight; fixed 15-minute minimum duration; limits (minimum duration, window end) rounded inward to the snap grid so the dragged edge stays on it. Each project's window runs from local midnight of its start date to the midnight after its end date (up to 366 days, `Project.window(zone)`); the take-home's fixed 3-day window from 2026-03-02 survives only as the first-launch sample project.
- Reason: exact round-trips, symmetric negatives, and a 13:30 task stays valid at 60-minute snap.

## 2026-11-11: Calendar time model

- Decision: four scales (Hours, Days, Weeks, Months). Zoom is the width of one nominal unit (a month is 365.25/12 days), but ticks follow the real calendar (local midnights, Mondays, the 1st). Snap options per scale (Hours 15m/30m/1h, Days 1h/6h/1d, Weeks 1d, Months 1d/1w); whole-day snaps follow local midnights. View settings are stored per project in the local-only `view_settings` table (Room v2, `MIGRATION_1_2`).
- Reason: multi-month projects need longer scales, and day-or-longer grids must survive DST.
- Added 2026-11-12 (user request): pinching the grid zooms too — spreading zooms in, pinching together zooms out — continuously within the scale's limits (`TimelineConfig.zoomedBy`), keeping the time between the fingers in place (`ViewportMath.rescaleAround`), live while pinching and saved once when the fingers lift. Once a second finger lands, every move and lift is consumed until all fingers are up, so a pinch never drags a bar, scrolls or taps.

## 2026-11-11: Task dependencies

- Decision: finish-to-start links only, stored in `dependencies` (Room v3, `MIGRATION_2_3`) with a project foreign key but no task foreign keys (a sync may deliver a link before its tasks). Cycles and self-links are blocked in the editor (`DependencyRules.forbiddenPredecessors`); violations (successor starts before predecessor ends) are drawn red, never auto-fixed by edits. Deleting a task tombstones its links; undo restores both.
- Reason: the common Gantt case, with no surprising automatic rescheduling. (A voice command can ask to push dependents — see Voice editing.)

## 2026-11-13: Task progress, notes and milestones

- Decision (user choices): a task has `progress` (0–100; 100 is done, set in one tap by "Mark done"), free-text `notes` (at most 2,000 characters, may be empty) and `isMilestone`. A milestone is a moment, not a span: it ends when it starts, lies inside the project (before its last midnight), is either done or not (progress 0 or 100), is drawn as a diamond, and is dragged but never resized (made only by the editor's switch, never by shrinking a bar). `SchedulerTask`'s `init` enforces the shape; `TaskRules` also reports notes that are too long and bad progress. Room v5 (`MIGRATION_4_5`) adds the three columns with defaults (not started, no notes, not a milestone).
- Sync: the three fields are optional on push (sync protocol rule 9): the server keeps the stored `progress` and `notes` when a push leaves them out, so an app from before them can't blank them; a push without `milestone` isn't one (such an app can't make one). Pulls always carry all three; the app defaults any that are missing and skips a pulled task that breaks the rules, and a stored row that breaks them is left out of reads rather than failing them.
- Notes stay private: never sent to the voice providers, never shown in the staff admin panel (which shows titles and dates only).
- Voice (user choice): the assistant can set progress ("mark design done", `set_progress`) and add milestones (`create_milestone`); the interpret context carries each task's `progress` and `milestone` (optional, for older apps), never its notes. A milestone moves like a task but has no length to change, and never counts as overlapping a task (a moment during work isn't a clash); dependency clashes still count.
- Reason: progress and milestones are the basic Gantt features the app lacked; the three changes share one migration and one sync-contract change.

## 2026-11-13: Projects as templates

- Decision (user choices): there is no separate template store; any project can be the starting point of another. "Duplicate" (project menu) and "Start from" (new-project sheet) run `CopyProject`: the copy keeps the source's length and starts on the chosen date; tasks move by whole calendar days in the zone (`CalendarDays.shift`), so local times survive DST, and a task DST would make invalid keeps its length instead; progress resets to 0; titles, notes, colours, row order, milestones and links are kept, with new ids. The project, its tasks and links are written in one Room transaction (`createProjectWithSchedule`) and sync like any other new rows.
- Reason: templates without a new kind of stored or synced data.

## 2026-11-11: Offline first, sync on request

- Decision: Room on the phone is the working copy; every write stamps `updatedAt` and marks the row dirty, and deletes are tombstones until synced. Sync never starts on its own: coming back online with unsynced changes shows a prompt, and only "Sync now" (prompt, button, account screen) runs `SyncEngine`. Conflicts: last write wins per record by edit time (server clock as ceiling, ties to the server); a stale push gets the winning copy back. Contract: `docs/sync-protocol.md`.
- Reason: a user choice (asked to be prompted rather than synced silently); per-record last-write-wins is simple and predictable.
- Constraint: once signed in, the app works fully without a connection; blocking Room and network work runs on `Dispatchers.IO`.
- Revised 2026-11-12 (user request): the app needs an account — signed out, it opens on the sign-in page (`AppGate`) and nothing else is reachable; signing out or deleting the account returns there. A build with no server URL can't sign in, so it stops at that page (user request, same day: sign-in is required even without a server).

## 2026-11-11: Backend — Laravel + Postgres + Filament on ECS Fargate + RDS

- Decision: replace the (never deployed) AWS serverless backend (Cognito, HTTP API, Lambda, DynamoDB; kept at git tag `serverless-backend-v1`) with a Laravel 13 app in `server/`: Postgres, Sanctum tokens, Filament 5 admin, hosted on ECS Fargate + RDS in the user's AWS account. The `/v1/sync` contract (`docs/sync-protocol.md`) is unchanged.
- Reason: the user wants an admin back-office (user management, health dashboard, staff roles + MFA + audit log, audited support data access) and the data is relational; cross-user reporting on DynamoDB would need extra pipelines.
- Trade-off: a database and containers that run all the time (≈ $60–70/month for dev on AWS, see DEPLOY.md) and auth flows that are now ours to secure.

## 2026-11-12: AWS shape

- Decision: CloudFront (default domain, HTTPS) → CloudFront VPC origin → internal ALB → ECS Fargate (ARM64) web + jobs; RDS Postgres in isolated subnets; no NAT gateway (tasks in public subnets, ingress only from the ALB); Secrets Manager for APP_KEY and DB credentials; SES via the task role; ECS Exec only on the jobs service (for `chronova:create-staff`). Voice provider keys live in a `VoiceKeys` secret (created empty, filled by hand) given only to the web container; voice is off unless deployed with `-c voice=on`, and deploying with it off is the kill switch.
- Reason: no domain needed, no way to bypass CloudFront (so `CloudFront-Viewer-Address` is trustworthy for rate limits), lowest fixed cost; provider keys never in code, the template or other containers.

## 2026-11-12: One apply path for schedule changes

- Decision: drags, the task editor, delete/undo and voice or typed commands all write through `ApplyScheduleChanges`: one Room transaction that applies a list of `ScheduleChange`s only if every record is still exactly what the change was computed from (content compare, not an `updatedAt` version) and returns the inverse list for Undo. A drag commits `commitEdit(original, edited)` with the task as it was at drag start.
- Reason: manual and voice edits share one tested path; edits that arrive meanwhile (another edit or a sync) are never overwritten, with no schema change.

## 2026-11-12: Voice editing

- Decision: the server only proposes. The phone records (the turn ends on silence; at most 60 s), the server transcribes (ElevenLabs Scribe, with the project's task titles as key terms) and interprets (OpenAI, strict JSON schema, re-validated by the server), and the app plans the operations against its current data. The assistant is voice-only (2026-11-12 revision; previously a sheet with a text field and an Apply button): a 3D orb says the change and asks, applies only on a spoken yes (no, silence or tapping away changes nothing; other words are a corrected request), then hides and says what changed while the task is highlighted; Undo follows. Before asking, the app works out the clashes the change would cause (new overlaps, newly broken dependencies); the assistant names them and asks whether to apply it anyway — clashes are allowed, never blocked. Spoken answers are written by OpenAI and said by ElevenLabs in the user's chosen voice (`OYTbf65OHHFELVut7v2H`). "Push everything after it" (dependents) is available only through commands. Voice is on for every signed-in user, bounded by per-user, per-endpoint rate limits, a kill switch and admin usage metrics. Provider keys stay on the server (one server key for everyone, never per-user or on the phone); OpenAI is called with `store: false`; no audio is stored. Revised 2026-11-12 (user choice): each user's requests and answers are kept 90 days in `voice_decisions` and sent back with that user's own requests as history, deleted with the account, never shown to staff (`VOICE_LEARN_DECISIONS` switches it off). Clashes get a clash-free alternative suggested by the model (`/resolve`) and re-checked by the app, which falls back to its own fix (user choice: model suggests, app checks). The interpreter's fixed rules are cached at OpenAI (explicit breakpoint, shared key); user content never is. Fakes are the default for development and tests.
- Reason: user choices (providers as in the brief, always preview then confirm with a spoken explanation, dependents via voice only, on for everyone, task names sent to the transcriber); a model can only propose allowed operations on listed tasks.
- Constraint: real providers only with the user's approval; the privacy policy and Play Data safety form must cover ElevenLabs and OpenAI before release (`docs/voice-api.md`).

## 2026-11-12: Branding

- Decision: the supplied logo (`docs/brand/chronova-logo.webp`) is used as is, not redrawn: `tools/brand/generate.py` cuts the adaptive, themed and launch-screen icons and the admin logo and favicons from it. The app stays light-themed; the admin panel is dark-only in the logo's colours.
- Revised 2026-11-13 (user choice): the app has a dark theme as well. It follows the phone by default, with a System / Light / Dark choice on the account page, stored on the device only (`chronova_appearance` preferences, kept when signing out). Dark uses slate surfaces (every Material surface container is set, so sheets and menus stay slate) and the same brand and task colours, so white bar labels stay readable. `MainActivity` re-applies edge-to-edge so the system bar icons follow the app's theme. Every screenshot test is also taken in dark.
- Reason: user choices (image as is; dark-only admin).
- Constraint: regenerate brand images with the script rather than editing them; the icon art must stay inside the adaptive icon's 66 dp safe circle.

## 2026-11-12: Modular three-layer architecture

- Decision: the Android app is split into Gradle modules: `:app` (shell and manual-DI composition root), `:core:{model,timeline,domain,database,network,data,designsystem,ui,testing}` and `:feature:{projects,timeline,notifications,sync,account,assistant}:{domain,data,presentation}`, with a layer module only where the layer has code (projects and timeline have no data module; they use `:core:data`). `presentation → domain ← data`; features never depend on each other; core never depends on features; `verifyModuleGraph` (build-logic) fails the build on a forbidden edge. Domain modules use the plain Kotlin JVM plugin. ViewModels read through read-only domain query interfaces (`ProjectQueries`, `NotificationQueries`, `AuthQueries`, `SyncStatusQueries`, `SyncPromptQueries`; the repository contracts extend them, so a ViewModel holding one can't write) and change data only through use cases (revised 2026-11-13, user choice: removes the read-only pass-through use cases; reads with logic stay use cases); ViewModels never get a repository, the HTTP client, the recorder or player, or `Context`. Cross-feature UI is wired by the shell (`AssistantSlot` in `:core:ui` for the assistant on the timeline). Build settings live in convention plugins (`build-logic/convention`).
- Reason: a user request (modular, clean three-layer architecture), following the canonical mobile modular clean-architecture standard; the compiler now enforces what was a convention (the audit found 31 layer violations, now 0).
- Constraint: new code goes in the module that owns it; a feature reaching another feature goes through `:app` or a `:core` contract; strings and images live with the module that uses them (shared ones in `:core:ui`/`:core:designsystem`).
- Use cases are wired only in `:app` — `AppContainer` holds the shared pieces and each feature has its own graph in `noel.chronova.app.di` (2026-11-13) — (tests build their own); domain holds no wiring helpers. `verifyModuleGraph` also checks test-only dependencies (tests may additionally use `:core:testing`).
- Decision (revised 2026-11-13, user choice: "everywhere, timeline too"; replaces the 2026-11-12 exception that let screens render domain models): screens and UI state use presentation-owned UI models (`TaskUi`, `ProjectRowUi`, `NotificationUi`, `AccountAuthUi`, `SyncUi`/`SyncPhase`, `ProposalLineUi`, …), mapped from the domain in a ViewModel or a `*UiMappers.kt` per feature. Domain entities, results and statuses never reach a composable; shared value vocabulary (`TaskColor`, `TimeScale`, `TimelineWindow`, `TimelineConfig`, notification kinds, `SyncFailure`) is allowed. The timeline's gesture engine (`:core:timeline` `TaskEdits`, `TaskVisibility`) works on `TimeSpan`s, and the ViewModel maps finished edits back by id. Helpers behind a ViewModel (the assistant's conversation, phrases) and the notification-shade adapter may use domain types. `verifyUiModels` (build-logic) fails the build when a file with composables, or a `*UiState.kt`/`*UiModels.kt` file, imports a denied domain type, and when a `*ViewModel.kt` file declares anything but its ViewModel (screen state lives in the checked files).
- Revised 2026-11-13 (user request): Kotlin packages match their modules (`noel.chronova.core.<module>`, `noel.chronova.feature.<name>.<layer>`, sub-packages kept; `:app` stays `noel.chronova.app`), so a package names its module. Stored data was unaffected (the notification undo JSON uses `@SerialName` type names; navigation routes stay in `:app`); Room's schema folder moved with the database class.

## 2026-11-12: Repository layout

- Decision: one git repository rooted at `Chronova/`, holding `server/`, `docs/`, `tools/brand/`, `README.md` and this AI kit; the Android Gradle project lives in `Chronova - Android App/` and holds only Android files.
- Reason: a user choice, so the Android folder is Android-only while backend and shared docs sit at the root. Moving the git root up kept all history (Android files are recorded as renames).
- Constraint: run Gradle from `Chronova - Android App/`, server and infra commands from `server/`; tools that write Android resources (`tools/brand/generate.py`) use the Android folder path.

## 2026-11-11: Platform and dependencies

- Decision: Filament 1.79.0 for the assistant orb (with a mic-icon fallback); `minSdk` 26 (for `java.time`, no desugaring); AGP 9.5.1 with built-in Kotlin plus the Compose compiler plugin 2.3.21; Compose BOM 2026.04.01; icons-core only (the minus glyph is defined locally); Room 2.8.4, Navigation Compose 2.9.8, OkHttp 5.3.2 with kotlinx-serialization 1.10.0, core-splashscreen 1.2.0; manual dependency injection (no DI framework). For the modules (2026-11-12): the Kotlin JVM Gradle plugin at the same Kotlin 2.3.21 (approved by the user); `core-ktx` 1.18.0 and `kotlinx-coroutines-core` 1.10.2 are now listed in the catalog at the versions the app already shipped transitively (no new artifacts). Every version is pinned exactly and was at least two weeks old when chosen; new dependencies need the user's approval.
- Revisit when: the user approves version upgrades (lint reports newer versions).

## 2026-11-11: AI kit stays local

- Decision: AI kit files (at the repository root) are excluded through `.git/info/exclude`; the canonical Android skill is referenced, not installed.
- Revised 2026-11-13 (user choice): `HANDOFF.md`, `DECISIONS.md` and `Learn/` are tracked in git, so a clone, CI or another developer gets the project's rules and decisions; the rest of the kit stays local-only.
- Reason: a user choice, to keep the PicCollage submission clean.
- Decision (2026-11-13, user request): screenshot tests use Google's Compose Preview Screenshot Testing through AGP test suites (experimental), engine pinned in the catalog. Coverage: shared UI and every screen, phone and 1.5× font. Reference PNGs live in the repo; images come from the preview engine on the Mac, so the on-device tests stay the check for behaviour. The convention plugin turns on Android resources in unit tests and the release variant's unit-test setup (required by the preview renderer) and skips running release unit tests. Notifications and account were split into stateless `*Content` composables for this.
- Decision (2026-11-13, user choice): one committed validation command, `tools/check.sh`, used by CI and a git pre-push hook. CI is GitHub Actions: macOS runs the JVM check with screenshots (references are Mac-rendered), Ubuntu with KVM runs the device tests on a Gradle managed emulator (`pixel6api35`). The phone stays for hands-on and stress checks (`tools/qa/rotation-stress.sh`).
