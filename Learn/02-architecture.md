# Architecture

## Layers

The app is split into Gradle modules: `:app` (shell and composition root), `:core:*`, and `:feature:<name>:{domain,data,presentation}` (a layer module exists only where the layer has code). Dependencies point inward: `presentation → domain ← data`; features never depend on each other; core never depends on features. `verifyModuleGraph` enforces this, and domain modules (and `:core:model`, `:core:timeline`, `:core:domain`) use the plain Kotlin JVM plugin, so an Android or Compose import there doesn't compile.

ViewModels read through read-only domain query interfaces (`ProjectQueries`, `NotificationQueries`, `AuthQueries`, `SyncStatusQueries`, `SyncPromptQueries`; the repository contracts extend them, so a ViewModel holding a query type cannot write) and change data only through use cases (one class per operation, grouped in a `<Feature>UseCases` holder; a read with logic, such as the schedule or the sync overview, is a use case too). Wiring is manual: `AppContainer` holds what features share, and each feature's graph in `app/.../di/` (`ProjectsGraph`, `TimelineGraph`, …) builds its queries and use cases; tests build their own. Each module's Kotlin package is its namespace (`noel.chronova.core.<module>`, `noel.chronova.feature.<name>.<layer>`). Screens render their own UI models (`TaskUi`, `ProjectRowUi`, `NotificationUi`, …), mapped from the domain in the ViewModel or a `*UiMappers.kt`; the timeline's gesture engine edits plain `TimeSpan`s, so it never touches a domain task, and `verifyUiModels` keeps domain entities out of composables. Repository interfaces and ports live in domain, implementations in data. Room entities stay in `:core:database`/`:core:data`, DTOs in the feature data modules. ViewModels expose `StateFlow` and take intents (`selectTask`, `commitEdit`, `undo`, config intents). Transient drag state lives only in `TaskLane`. Cross-feature UI goes through the shell: the timeline hosts the assistant only through `AssistantSlot` (`:core:ui`), which `ChronovaNavHost` builds with `rememberAssistantSlot`; the shell reads the project id from `ProjectTimelineRoute` and passes it to both ViewModel factories.

Every schedule change — a drag, the task editor, delete/undo, a voice or typed command — goes through `ApplyScheduleChanges`: one Room transaction that refuses changes computed from a copy that has changed since (a later edit or a sync) and returns the inverse for Undo. A drag commits as `commitEdit(original, edited)`, where `original` is the task at drag start.

## Coordinates

`pxPerMinute = unitWidthPx / scale.minutes`; `x = (t − origin) min × pxPerMinute`. The origin is the project window's start (local midnight of the project's start date, `Project.window(zone)`). Pixel → time rounds to whole minutes, because Float precision at large x is coarser than 1 ms. Changing zoom or scale only changes `pxPerMinute`. Ticks follow the real calendar (`TimelineMath.ticks`), and whole-day snapping follows local midnights, so DST days don't shift the grid.

## Scrolling

`TimelineViewportState` is the only horizontal scroll position. The body and `TimelineHeader` both drive it with `scrollable(…, reverseDirection = true)`; header and grid read it while drawing, bars in their `offset {}` (layout phase). Content is never measured at full width (a long project at high zoom exceeds what Compose can measure): bars are clipped to a page-quantized span. Rows are a `LazyColumn` of `[TaskNameCell | TaskLane]`. Zoom anchoring happens in `viewport.onGeometry` from a `SideEffect`, before layout/draw of the same frame.

## Gestures

`detectTaskGestures` decides tap vs horizontal drag after touch slop and consumes the event, so the parent scroll doesn't move. `HandleHitTest.modeAt` fixes the `GestureMode` at pointer-down. `TaskEdits.apply(…)` recomputes the bar from the gesture-start task plus the total distance on every event. The preview is kept until the committed task arrives, which avoids a one-frame flash.

## Data and sync

Writes stamp `updatedAt` and mark rows dirty; deletes are tombstones until synced. Sync never starts on its own: only "Sync now" (reconnect prompt, button, account screen) runs `SyncEngine` — push dirty records in batches, pull every page since the cursor, apply parents first, save the cursor. Last write wins per record; the contract is `docs/sync-protocol.md`.

## Voice

The assistant (`:feature:assistant`) records until silence (`ListenForSpeech`), the server transcribes and interprets, and `AICommandHandler` turns the proposal into commands that `CommandPlanner` plans against the schedule *as it is now* (`InterpretRequest`, which also finds a clash-free alternative). The orb says the change and asks; only a spoken yes writes, through `ApplyScheduleChanges`, with Undo after. `AssistantViewModel` opens and closes the orb and handles undo and messages; the spoken conversation itself is the plain-Kotlin `AssistantConversation`, sharing the screen state through `AssistantSession`. Every server, data and hardware step is a use case. Contract and privacy: `docs/voice-api.md`.

## Server

`server/` is Laravel 13 on Postgres. `/v1/sync` runs one transaction per request with the user's row locked; `/v1/auth` is enumeration-safe; the admin panel uses a separate `staff` guard with required MFA and an append-only audit log. Voice endpoints keep provider keys on the server, constrain the model with a strict schema and re-validate its answer; no audio or words are stored or logged.

## Gotchas

- Key `pointerInput` on `task.id` and read the latest values through `rememberUpdatedState`. Keying on the task object restarts the gesture mid-drag.
- A bar container wider than its lane needs `wrapContentWidth(unbounded = true)` before `width`, or it gets clamped.
- Blocking Room calls (e.g. `clearAllTables`) and network body reads must run on `Dispatchers.IO`; JVM tests don't catch main-thread violations, the phone does.
- `Icons.Filled.Remove` is not in icons-core. Segmented buttons use `secondaryContainer`, so keep it on brand.
- Filament container-width columns (`@md`, …) only work on a component marked `gridContainer()`.

## Invariants in detail

Moved from `HANDOFF.md` (13 Nov 2026), which keeps a short version.

- Time is stored as epoch millis. Pixels are always derived through `TimelineMath` from one origin (the window start), and pixel → time rounds to whole minutes.
- Projects, tasks and dependencies live in Room (`:core:database`, schema v6 with migrations; `schemas/` there); screens read them through `ProjectRepository` flows; every write stamps `updatedAt` and sets `dirty`; deletes are tombstones until synced. The shell reads the project id from the `ProjectTimelineRoute` nav argument and passes it to the timeline and assistant ViewModel factories.
- There is exactly one horizontal scroll position, `TimelineViewportState`. Header, grid and bars read it in the draw/layout phase; nothing is measured at full content width (long projects exceed Compose's layout limits). Bars are clipped to `ViewportMath.composeSpan`. Every task has a row at every zoom (the list is virtualized); a row whose bar is off-screen shows an arrow towards it that scrolls it into view.
- Each bar has one `pointerInput` (`detectTaskGestures`), keyed on the task id; `GestureMode` is fixed at pointer-down; edits are recomputed from the gesture-start task plus the total distance (never snap per event). Pinch-to-zoom (`detectPinch` on the grid, Initial pass) consumes every move and lift from the second finger until all fingers are up, so it never reaches the bars; its zoom is anchored between the fingers in `onGeometry` and saved once on release.
- Snapping goes through `SnapGrid`: sub-day via `TimeSnapper` (`floorDiv`, nearest, ties later) from the window start; whole days via `DayGrid` on local midnights (DST-safe). Ticks are calendar-based. The 15-minute minimum and other limits round inward to the grid. Configuration never changes timestamps.
- Every schedule write goes through `ApplyScheduleChanges`: one transaction that refuses changes computed from an outdated copy and returns the inverse for Undo. Drags commit `commitEdit(original, edited)` and never move other tasks.
- Three layers, enforced by modules: `presentation → domain ← data`; features never depend on each other (cross-feature UI is wired in `:app`, e.g. the timeline hosts the assistant only through `AssistantSlot` in `:core:ui`); core never depends on features. `verifyModuleGraph` fails on a forbidden edge. Domain modules and `:core:model`/`:core:timeline`/`:core:domain` use `chronova.jvm.library` (no Android plugin), stay free of Android and are unit-tested.
- ViewModels read through read-only domain query interfaces (`*Queries`, which the repository contracts extend) and change data only through use cases (one class per operation, grouped in a `<Feature>UseCases` holder, wired only in `:app`: `AppContainer` for shared pieces, one graph per feature in `app/.../di/`; tests build their own); repository interfaces and ports live in domain, implementations in data; Room entities and DTOs never leave the data side. Screens and UI state render presentation-owned UI models, mapped in the ViewModel or a `*UiMappers.kt`; `verifyUiModels` fails when a file with composables or a `*UiState.kt`/`*UiModels.kt` file imports a domain entity, result or status (value enums and view settings are fine), or when a `*ViewModel.kt` declares anything but its ViewModel (put screen state in `*UiState.kt`/`*UiModels.kt`). Strings and images belong to the module that uses them; ones several features share go in `:core:ui` / `:core:designsystem`. Icon buttons with a count badge use `BadgedIconButton` (`:core:ui`): Material's `IconButton` clips its content and cuts a "99+" badge.
- Sync never starts on its own: only "Sync now" (reconnect prompt, button, account screen) runs `SyncEngine`. Blocking Room calls and network body reads run on `Dispatchers.IO`.
- Brand images come from `tools/brand/generate.py`; regenerate, never hand-edit.
- Rotation recreates `MainActivity` (no `configChanges`): view models survive, plain `remember` doesn't, so dialog/sheet/notice flags use `rememberSaveable` or a view model. `ON_STOP` also fires on rotation: anything that means "the user left" checks `activity.isChangingConfigurations` first. While `AssistantViewModel.busy` (orb open, or still saying what it changed) the timeline holds the screen's orientation (`SCREEN_ORIENTATION_LOCKED`) and gives it back afterwards. The big assistant overlay switches to orb-beside-words when wider than tall.

## Server rules

- The sync contract is `docs/sync-protocol.md`; `server/tests/Feature/SyncTest.php` holds its conformance scenarios and must keep passing. The old serverless backend lives only at tag `serverless-backend-v1`.
- Auth answers never reveal whether an email has an account. `/v1` guests always get 401 JSON (never a redirect). The `staff` guard is separate from app users; MFA is required; every privileged admin action is written to the append-only audit log; user pages never show project content (only an active, audited data-access grant does).
- Behind CloudFront (`BEHIND_CLOUDFRONT=true`) client IPs come from `CloudFront-Viewer-Address`, never `X-Forwarded-For`.
- Voice (`docs/voice-api.md`): the server only proposes (strict schema, re-validated); the app plans against current data; the assistant is voice-only (a 3D Filament orb, `voice/orb/`), says the change and writes only on a spoken yes, then hides and says what changed while the task is highlighted. If the orb's native start-up ever aborts, a `chronova_orb` pref makes it fall back to the mic icon for that app version (clear app data to retry); previews and screenshots (`LocalInspectionMode`) always get that fallback. Keys stay on the server; no audio or words are stored or logged; fakes are the default.
