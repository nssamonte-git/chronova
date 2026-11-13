# Project Map

## App

Gradle modules under `Chronova - Android App/` (see `settings.gradle.kts`); each module's package matches its path: `noel.chronova.core.<module>` and `noel.chronova.feature.<name>.<layer>` (with sub-packages such as `.components`, `.schedule`, `.orb`), under `src/main/kotlin/`; `:app` keeps `noel.chronova.app` under `src/main/java/`.

| Area | Module | Go here for |
|---|---|---|
| Shell | `:app` | `ChronovaApp`/`AppContainer` (manual DI: builds repositories and use cases), `MainActivity`, `navigation/` (routes, `AppGate`, NavHost that wires features and the assistant slot), `BackendConfig` (`chronova.apiUrl`), manifest, launcher icons, instrumented tests |
| Build conventions | `build-logic/convention` | `chronova.android.application/library/compose/room`, `chronova.jvm.library` (domain), `chronova.module.graph` (`verifyModuleGraph`) |
| Models | `:core:model` | `Project`, `SchedulerTask`, `TaskDependency`, `TimelineConfig`, `TimeScale`, `TimelineWindow` |
| Timeline engine | `:core:timeline` | time↔x math, calendar ticks, snap grids, move/resize edits, handle hit-testing, viewport math |
| Shared domain | `:core:domain` | `ProjectRepository`, `NotificationRepository`, auth and sync contracts (`domain.auth`, `domain.sync`), rules, `ScheduleChange`, `ApplyScheduleChanges` (one transaction, stale-checked, undoable), sample project |
| Database | `:core:database` | Room (v4: projects, tasks, dependencies, view settings, notifications), DAOs, migrations, `schemas/` |
| Shared data | `:core:data`, `:core:network` | `RoomProjectRepository` and mappers, first-launch flag, connectivity; HTTP client, `ApiJson`, `Call.await()` |
| Shared UI | `:core:designsystem`, `:core:ui` | theme, task colours, shared icons and launch logo; `TimeFormat`, sync status line/button, shared strings, `AssistantSlot` |
| Test fakes | `:core:testing` | `FakeProjectRepository`, `FakeNotificationRepository` |
| Projects | `:feature:projects:{domain,presentation}` | list/save/delete use cases; project list and editor |
| Timeline | `:feature:timeline:{domain,presentation}` | schedule, edit, task editor, delete/undo, view-settings use cases; timeline screen, ViewModel, editor sheet, `presentation/components/` (`SchedulerTimeline`, `TimelineViewportState`, `TaskLane`/`TaskBar`/`TaskGestures`, header, grid, arrows, controls) |
| Notifications | `:feature:notifications:{domain,data,presentation}` | use cases and `NotificationPoster`; `RoomNotificationRepository`; page, wording, `SystemNotifications` |
| Sync | `:feature:sync:{domain,data,presentation}` | `SyncService` and use cases; `SyncEngine`, `SyncCoordinator`, `RoomLocalSyncStore`, sync API/DTOs; sync ViewModel and reconnect prompt |
| Accounts | `:feature:account:{domain,data,presentation}` | sign-in/account use cases; `ApiAuthRepository`, keystore token store; sign-in pages and account page |
| Voice assistant | `:feature:assistant:{domain,data,presentation}` | ports, `AICommandHandler`, `CommandPlanner`, clashes, `AssistantUseCases`; OkHttp voice client, recorder, player, settings; `AssistantViewModel`, overlay, Filament orb (`voice/orb/`, `assets/orb`), `rememberAssistantSlot` |
| Export | `:feature:export:{domain,data,presentation}` | `ExportProject`, `CsvExport`, `IcsExport`; `GanttPdfWriter` (PdfDocument), `CacheExportFiles` + FileProvider; `ExportViewModel`, `ExportSheet` (shown by the shell from the project menu) |
| Tests | each module's `src/test/…`; `:app` `src/androidTest/…` | domain and engine tests in the pure modules, ViewModel tests with fake use cases/repositories, data tests; Room transaction and rotation on a device |

## Server and the rest

| Area | Path | Go here for |
|---|---|---|
| API | `server/routes/api.php`, `server/app/Http/Controllers/Api/` | `/v1/auth`, `/v1/sync`, `/v1/account`, `/v1/voice/*` |
| Voice providers | `server/app/Voice/`, `server/config/voice.php` | interpreter, transcriber, explainer and speaker (real and fake), schema and validation, limits |
| Admin panel | `server/app/Providers/Filament/AdminPanelProvider.php`, `server/app/Filament/`, `server/app/Admin/` | staff panel, resources, dashboard widgets, metrics, audit |
| Data model | `server/app/Models/`, `server/database/migrations/` | users, projects, tasks, dependencies, staff, audit, sync and voice events |
| Server tests | `server/tests/Feature/` | auth, sync conformance, voice, admin |
| Container | `server/Dockerfile`, `server/docker/`, `server/compose.yaml` | image roles, local Postgres and Mailpit |
| AWS | `server/infra/` | CDK stack, tests, `DEPLOY.md` |
| Docs | `README.md`, `docs/` | reviewer docs, sync and voice contracts, AI log, AI writeup |
| Brand | `tools/brand/generate.py`, `docs/brand/` | icons and logos generated from the supplied logo |
