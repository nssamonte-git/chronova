# Chronova — AI Usage Log

Factual record of AI-assisted development. Entries are appended per batch; only issues that actually occurred are recorded.

## Tools

- **Claude Code (Claude Opus 5.5)** in the Claude desktop app — project inspection, planning, code generation, build/test execution, git commits.

## How work was divided

1. A detailed implementation prompt (requirements F01–F09, architecture, testing, AI-log rules) was given to Claude Code with the instruction to *revalidate it, then plan in small practical batches* before writing code.
2. Claude inspected the generated Android Studio project, checked the local toolchain and Gradle cache, asked three clarifying questions (mockup availability, git repo location, drag-vs-scroll gesture model), and produced a 10-batch plan (B0–B9). The plan was reviewed and approved before any code was written.
3. Each batch ends with a real build/test run, an entry here, and a commit.

## Batch log

### Plan revalidation (before B0)

Corrections to the original prompt, found by inspecting the actual project:

| Prompt assumption | Reality | Decision |
|---|---|---|
| Compose project, `com.chronova` package | Views "No Activity" template; namespace `noel.chronova.app`; no activity | Add Compose + `MainActivity`; keep the existing namespace |
| Standard Kotlin plugin setup | AGP 9.5.1 has built-in Kotlin; only the Compose compiler plugin is needed | Apply `org.jetbrains.kotlin.plugin.compose` only |
| (unstated) | `minSdk 24`, but `java.time` needs API 26 | Raise `minSdk` to 26 instead of adding desugaring |
| (unstated) | Timeline range, snap rounding rule, minimum duration, drag-commit model not specified | Fixed 3-day range; round-to-nearest with `floorDiv`; 15-min minimum; local preview from gesture-start values, commit on drag end |
| "Use the previously provided mockup" | Mockup not in the repository | Proceed from the written design principles and palette |

### B0 — Bootstrap

- Template committed unchanged as the baseline, then Compose BOM 2026.04.01 / Material 3 / activity-compose / lifecycle-compose added; Views-only deps (`appcompat`, `material`) and template example tests removed; XML theme reduced to a platform `NoActionBar` window theme; `MainActivity` + `ChronovaTheme` + placeholder `ChronovaScreen` added.
- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL** (47 s).

**AI mistake — emulator availability.**
1. *What AI said:* the plan stated that system image `android-35 google_apis` was installed and an AVD could be created from it.
2. *Why wrong:* the `system-images/android-35/google_apis` directory exists but is empty.
3. *How discovered:* listing the directory when trying to launch the app in B0.
4. *Correction:* manual verification uses the connected physical device instead (requires USB-debugging authorization) or a system image download.
5. *Decision:* don't infer installed tooling from directory names; check contents before planning on it.

### B1 — Timeline calculation engine

- Added `SchedulerTask`, `TimelineConfig`/`TimeScale`/`TimelineWindow`, and pure `TimelineMath`, `TimeSnapper`, `TaskEdits`, `GestureMode` (no Android dependencies).
- 24 JUnit tests cover all 8 required scenarios plus tie-breaking, window clamping, tick generation, and cumulative-rounding avoidance.
- First run of `./gradlew :app:testDebugUnitTest`: **24 tests, 2 failed**. After the fix below: **24 tests, 0 failed**.

**AI mistake — Float precision in pixel → time conversion.**
1. *What AI generated:* `pxToMillis` converted pixels to milliseconds exactly (`px / pxPerMinute * 60_000`, rounded to the millisecond), and `xToTime` used it.
2. *Why wrong:* x positions are `Float`. At x ≈ 1300 px a Float only resolves about 1e-4 px, which is several milliseconds at 96 dp/hour, so time → x → time came back 1 ms early (`…200000` → `…199999`).
3. *How discovered:* the round-trip tests `xToTime_inverseOfTimeToX` and `zoomChange_preservesTimestamps` failed on the first test run.
4. *Correction:* pixel → time now rounds to whole minutes (`(px / pxPerMinute).roundToLong() * 60_000`).
5. *Decision:* the timeline's real resolution is one minute (sub-minute differences are invisible on screen and snapping is ≥ 15 min), so minute rounding is the right precision rather than switching coordinates to Double.

### B2 — Scheduler state and sample tasks

- `SampleTasks` (4 required tasks on fixed reference date 2026-03-02, 3-day window from local midnight), `ChronovaUiState`, `ChronovaViewModel` (`StateFlow`; intents `selectTask`, `commitEdit`, `updateConfig`), `TimeFormat`, and `ChronovaScreen` collecting state with `collectAsStateWithLifecycle()`.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` — **BUILD SUCCESSFUL, 30 tests, 0 failed** (6 new ViewModel tests).
- First on-device launch (physical phone, Android 10 / API 29, 360 dp wide): app starts, top bar shows "Chronova / Shape Your Time", sample tasks list 09:00–11:00, 11:00–14:00, 13:30–16:00, 16:00–17:00.
- No AI mistakes recorded in this batch.

### B3 — Time axis, grid and task bars

- `SchedulerTimeline` (fixed name column + header + vertically scrolled rows), `TimelineHeader` (canvas; reads the horizontal `ScrollState` in the draw phase), `TimelineGrid`, `TaskBar`, `TaskNameColumn`, `TaskColor.toColor()` (blue/green/purple from the M3 scheme, amber from the palette). Timeline forced to LTR.
- One horizontal `ScrollState` owned by the body's `horizontalScroll`; initial scroll to one unit before the first task (saved, so it runs once).
- `./gradlew :app:testDebugUnitTest :app:installDebug` — **BUILD SUCCESSFUL, 30 tests, 0 failed**.
- On device: opens at 08:00; UI Design's left edge and the 09:00 grid line are both at x = 448 px. After three slow swipes, bar edges sit on 11:00, 13:30, 14:00 and 16:00 lines and header labels stay aligned with the grid.

**AI mistake — batch boundary in the plan.**
1. *What AI planned:* B3 renders bars "with no gestures/scrolling", and B4 adds the shared scroll.
2. *Why unsuitable:* the timeline origin is 00:00 and the first task is at 09:00 (~860 dp right), so without scrolling none of the bars are on screen and B3 could not be verified.
3. *How discovered:* while implementing B3, before running it.
4. *Correction:* the single horizontal `ScrollState` and the initial scroll position moved into B3; B4 keeps header swipe-to-scroll, end-of-range and rotation checks.
5. *Decision:* each batch must be verifiable on its own; the scroll architecture was unchanged, only its timing.

### B4 — Scroll synchronization

- Header swipes now scroll the timeline through `Modifier.scrollable(sameScrollState, reverseDirection = true)` — still one scroll position, no second `horizontalScroll`.
- Corner cell shows the day at the viewport's left edge (`derivedStateOf`, so it recomposes only when the day changes); added after on-device checks showed no date context between midnight labels.
- `./gradlew :app:testDebugUnitTest :app:installDebug` — **BUILD SUCCESSFUL, 30 tests, 0 failed**.
- On device: a slow header swipe moved header and body together (08:00 → 10:00 view); 12 fast flings reached the end of the range (22:00–23:00, Wed 4 Mar) and 14 back returned to Mon 2 Mar 00:00, with labels aligned to grid lines throughout; rotating to landscape (after enabling auto-rotate on the phone) kept the scroll position, tasks and day label; vertical scroll in landscape moved names and rows together.
- No AI mistakes recorded in this batch. Observation for B7: in landscape on a 360 dp-tall phone the top app bar takes a large share of the height.

### B5 — Selection and drag-to-move with snapping

- `detectTaskGestures` (one handler per bar: tap vs horizontal drag, mode chosen at pointer-down, total distance from the down position reported on every move), `TaskRow` (local preview from the gesture-start task, `TaskEdits.apply`, commit on release, revert on cancel, haptic tick when the snapped time changes, `pointerInput` keyed on task id with `rememberUpdatedState`), `TaskBar` selected/active states, tap-on-empty-space to deselect.
- `./gradlew :app:testDebugUnitTest :app:installDebug` — **BUILD SUCCESSFUL, 30 tests, 0 failed** (after fixing a brace error from a scripted edit; compile error, caught by the build).
- On device (15-min snap): tap outlines UI Design; mid-drag the bar is raised and shows the snapped live range "09:30 – 11:30"; +150 px (≈ +47 min) committed 09:45–11:45 and −200 px (≈ −62.5 min) committed 08:45–10:45, exactly as predicted by the engine; the header stayed at 08:00 during both drags (no accidental scroll); a swipe over empty row space scrolled the timeline without moving bars; tapping empty space cleared the selection.
- Not verified on device: haptic tick (not observable via screenshots), gesture-cancel path, 30/60-min snapping (no UI to change it until B7; covered by unit tests).

**AI mistakes caught during implementation (before running):**
- *Drag lag:* the first draft of the detector started the total distance from the over-slop amount, so the bar would trail the finger by the touch slop (~8 dp). Changed to measure from the pointer-down position.
- *One-frame flicker:* the first draft cleared the preview on release, before the committed task arrived from the `StateFlow`, which would snap the bar back to its old position for a frame. The preview is now kept until the new task arrives (`LaunchedEffect(task)`).
Both were found by reviewing the generated code against the gesture design, not by observing the bug on device.

### B6 — Left/right resize

- Selected bars get a white grip on each edge and a touch container extended 20 dp beyond each edge. `HandleHitTest.modeAt` (pure, unit-tested) picks `RESIZE_START` / `RESIZE_END` / `MOVE` at pointer-down: 20 dp outside + up to 20 dp inside each edge, shrinking to a third of the bar on narrow bars so the middle third always moves. Unselected bars only move. Resize goes through the same single detector and `TaskEdits.apply`, so move and resize cannot run together.
- `./gradlew :app:testDebugUnitTest :app:installDebug` — **BUILD SUCCESSFUL, 33 tests, 0 failed** (3 new hit-zone tests).
- On device (15-min snap, UI Design selected): right edge −160 px → 09:00–10:15 (start kept); left edge +100 px → 09:30–10:15 (end kept); left edge −80 px → 09:00–10:15; left edge dragged past the end → clamped to 10:00–10:15 (15-min minimum); right edge dragged past the start → stayed 10:00–10:15; middle of the 15-min bar +96 px → moved to 10:30–10:45. All matched values predicted from the engine before running.
- Not yet verified on device: resizing with 60-min snap on QA Testing (13:30 start) — needs the B7 snap control; covered by `minimumDurationHoldsEvenWithCoarseSnap` and `resizeEnd_preservesStart` unit tests.
- Known limitation: dragging a handle past the viewport edge does not auto-scroll; the user scrolls and continues.
- No AI mistakes recorded in this batch.

### B7 — Configuration controls

- Zoom/scale/snap/row-height rules moved into `TimelineConfig` and `TimeScale` (per-scale default and limits, ×1.25 zoom steps, 40–80 dp rows in 8 dp steps), with ViewModel intents `setTimeScale`, `zoomIn/zoomOut/resetZoom`, `setSnapMinutes`, `changeRowHeight`. `TimelineControls` is a horizontally scrollable toolbar (segmented Hours/Days and 15/30/60 m, −/+ steppers with percentage and dp readouts, zoom reset); the top-bar settings icon shows/hides it. Changing zoom or scale keeps the centre time fixed (`KeepCenterTimeOnZoom`).
- `./gradlew :app:testDebugUnitTest :app:installDebug` — **BUILD SUCCESSFUL, 39 tests, 0 failed** (6 new `TimelineConfigTest`, ViewModel config test rewritten for the new intents).
- On device (360 dp portrait): zoom 100 → 125 → 156 → 100 % put the 09:00 line at x = 438, 425, 448 px, exactly as predicted for a fixed centre time (≈ 09:12); with 60-min snap a +100 px move gave 10:00–12:00 and a −120 px left-edge resize gave 09:00–12:00 (end kept); row height +2 steps → 72 dp; Days scale kept all task times; the settings icon hid and showed the toolbar.
- Known limitation: after a zoom change the content is drawn for one frame at the old scroll position before the anchor scroll is applied.
- Not verified on device: 60-min resize specifically on QA Testing (13:30 start); covered by unit tests.

**AI mistake — icon availability.**
1. *What AI generated:* `Icons.Filled.Remove` for the minus buttons, after adding `material-icons-core`.
2. *Why wrong:* `Remove` is only in `material-icons-extended`, a much larger library.
3. *How discovered:* compile error (`Unresolved reference 'Remove'`).
4. *Correction:* the single glyph is defined locally with `materialIcon { materialPath { … } }` from its Material path data.
5. *Decision:* don't add the extended icon set for one icon.

**AI mistake — Days scale showed no time labels.**
1. *What AI generated:* the header labelled only major ticks (every unit). In Days scale those are midnights only.
2. *Why wrong:* at the default Days zoom the viewport spans ~12 hours, so most positions showed a header with no text at all.
3. *How discovered:* on-device screenshot after switching to Days.
4. *Correction:* minor ticks (6 h in Days, 30 min in Hours) are labelled in a lighter style whenever they are at least 56 dp apart.
5. *Decision:* label density is decided by on-screen spacing, not by tick type.

**AI mistake — off-brand selection colour.**
1. *What AI generated:* a colour scheme without container roles.
2. *Why wrong:* Material 3 segmented buttons use `secondaryContainer` for the selected state, which fell back to the default lavender.
3. *How discovered:* on-device screenshot of the toolbar.
4. *Correction:* defined `primaryContainer`, `secondaryContainer` (brand blue tint) and `tertiaryContainer`.

### B8 — Optional polish

- Skipped by the user's decision after B7, in favour of documentation and final verification.

### B9 — Documentation and final verification

- `./gradlew clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL, 39 tests, 0 failed; lint 0 errors, 9 warnings**.
- Lint fixes: moved `mipmap-anydpi-v26` to `mipmap-anydpi` (minSdk 26); `TimeFormat` no longer captures `Locale.getDefault()` in static fields (clock pattern uses `Locale.ROOT`; day names format with the current locale); suppressed `FrequentlyChangingValue` on the deliberate unobserved read in the zoom anchor, with a comment. Re-run: **lint 0 errors, 5 warnings** — all "newer version available" (Gradle 9.9.1, Compose BOM 2026.09.00, lifecycle 2.11.0, Kotlin 2.4.21); versions intentionally left pinned and documented in the README.
- **AI mistake caught during the lint fix (before running):** building the day formatter per call made the corner day label allocate a formatter on every scroll frame (it is computed in `derivedStateOf`). Changed to derive a day index per scroll and format once per day.
- Final device smoke check: cold launch OK (`am start -W` TotalTime 5612 ms for the debug build on this low-memory Android 10 phone; release build not measured); corner label switched to "Tue 3 Mar" when scrolled to 22:00 on day 2; no FATAL EXCEPTION or ANR entries in logcat.
- Added `README.md` and `docs/AI_USAGE_WRITEUP.md` (summary for the shared Google Doc).

### B10 — Snap-grid fixes from a requirements review

- The user asked Claude Code to check the app against the brief. It met every requirement, but the review found two cases where clamping left a dragged edge between grid lines. The user approved fixing both.
- `TimeSnapper.floor` and `TimeSnapper.ceil` added. `TaskEdits` now rounds each limit inward to the snap grid: the minimum-duration limit in both resizes, and the window-end limit in move. The 15-minute minimum and the "opposite edge never moves" rule are unchanged.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL, 44 tests, 0 failed; lint 0 errors, 5 warnings** (the same version-update warnings). Five tests were added. `minimumDurationHoldsEvenWithCoarseSnap` now expects 14:00 instead of 13:45.
- Not verified on device: covered by unit tests only, because the change is in pure `core/` logic and the gesture code is untouched.

**AI mistake — clamps ignored the snap grid.**
1. *What AI generated:* clamps of `start + 15 min` (resize end), `end − 15 min` (resize start) and `window end − duration` (move), applied after snapping.
2. *Why wrong:* at 60-minute snap, shrinking a 13:30 task gave a 13:45 end. Moving a 2 h 30 task to the window end gave a :30 start. In both cases the dragged edge landed between grid lines.
3. *How discovered:* reading `TaskEdits` during the requirements review.
4. *Correction:* each limit is rounded inward to the nearest grid line that still respects it.

**AI mistake (caught before coding) — proposed fix conflicted with a recorded decision.**
1. *What AI generated:* a suggested minimum duration of max(15 min, snap interval).
2. *Why wrong:* the README already records rejecting a per-snap minimum, because a 13:30 task couldn't be kept at 60-minute snap.
3. *How discovered:* searching the docs for "minimum" before editing.
4. *Correction:* the minimum stays 15 minutes. Only the dragged edge is rounded to the grid.

### B11 — Multi-project foundation (Room, navigation)

- The user asked for a plan to make the scheduler work for any project. After clarifying questions, the user chose: work on `main`, Room, Navigation Compose, offline-first sync to AWS (API Gateway + Lambda + DynamoDB via CDK, Cognito, last write wins, user prompted on reconnect), and a v1 with task CRUD, project date ranges, Weeks/Months scales and dependencies. The pre-change submission is tagged `submission-v1`.
- New dependencies (user-approved): Room 2.8.4 + KSP 2.3.4, Navigation Compose 2.9.8, kotlinx-serialization 1.10.0, kotlinx-coroutines-test 1.10.2 (tests). KSP was confirmed to work with AGP 9.5.1's built-in Kotlin with a build before any code was written.
- `SchedulerTask` gained `projectId` and `sortOrder`; new `Project` (inclusive date range, window in the device zone). Room tables `projects` and `tasks` keep `updated_at`, `deleted` and `dirty` columns for the coming sync; the schema is exported to `app/schemas`. `ProjectRepository` (interface in `domain/`, Room implementation in `data/`), manual DI through `ChronovaApp`/`AppContainer`. The sample project is seeded once on first launch (a SharedPreferences flag, so deleting it later won't bring it back).
- Screens: project list → `ProjectTimelineRoute(projectId)`. `ChronovaViewModel` became `ProjectTimelineViewModel` (reads the repository, commits edits to it); it reports `NotFound` if the project disappears.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL, 51 tests, 0 failed; lint 0 errors, 12 warnings** (all "newer version available"). One `UseKtx` warning on the new SharedPreferences write was fixed. New: `ProjectTimelineViewModelTest` (8, with a fake repository), `ProjectTest` (5, including a 23-hour DST day).
- On device (Android 10, 720×1600): the list showed "Sample project, 2 Mar – 4 Mar 2026, 4 tasks"; dragging UI Design 192 px left (one hour at 100 % zoom) gave 08:00–10:00 as predicted, and it was still 08:00–10:00 after `am force-stop` and relaunch; the back arrow returned to the list; the crash buffer was empty. One tap after the cold start (7.4 s on this debug build) landed before the list was drawn and did nothing; a second tap worked.

### B12 — Virtualized timeline viewport

- Reordered ahead of project editing (plan batch 5 → B12): user-defined ranges can make the content wider than Compose can measure (e.g. 31 days at the 288 dp/h maximum zoom is ~430k px at 2× density; Compose constraints top out near 262k px), so the viewport had to change before ranges became editable.
- `horizontalScroll` + `verticalScroll` replaced by `TimelineViewportState` (one scroll position, driven by `scrollable` on the body and the header) and a `LazyColumn` of `[TaskNameCell | TaskLane]` rows. Header and grid draw only visible ticks (`TimelineMath.visibleTicks`, binary search); bars are clipped to `ViewportMath.composeSpan` (page around the scroll ± one viewport) and placed against the scroll in the layout phase. `HandleHitTest` now works in content coordinates. Zoom anchoring moved into a `SideEffect`, which removes the old "one frame at the old scroll position" limitation.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL, 56 tests, 0 failed; lint 0 errors, 12 warnings** (versions only). New `ViewportMathTest` (5); `HandleHitTestTest` adapted to content coordinates.
- On device: opened with 07:00 at the left edge (earliest task 08:00 minus one unit), as predicted; a slow 384 px body swipe scrolled ~366 px (touch slop) with the header, grid and bars still aligned (10:00 line = UI Design end, 11:00 line = Implementation start); zoom 100 → 125 % put the 10:00 line at x = 460 px, the predicted value for a fixed centre time; resizing the selected UI Design bar's right edge +120 px (30 min) gave 08:00–10:30. Crash buffer empty.
- Not verified on device yet: a project long enough to exceed the old layout limit (no UI to create one until B13).

**AI mistake — wide bars were silently narrowed.**
1. *What AI generated:* the bar container used `Modifier.width(…)` inside a lane only as wide as the viewport.
2. *Why wrong:* `width` respects the parent's max constraint, so any bar wider than the visible lane was clamped to the lane width; after zooming to 125 %, the 2-hour UI Design bar ended 16 px short of its 10:00 grid line.
3. *How discovered:* on-device screenshot after zooming, then pixel measurement (bar end 443 px vs. grid line 460 px; the narrower pre-zoom bar was exact).
4. *Correction:* `wrapContentWidth(Alignment.Start, unbounded = true)` before `width`; re-measured on device, bar end and grid line both at 465 px.
5. *Decision:* recorded as a constraint in the scroll decision.

**Tooling note:** one `adb shell am start -W` hung after an install (phone awake and unlocked); later launches used `am start` without `-W`.

### B13 — Project create, edit and delete

- `ProjectRules.validate` (domain): blank name, end before start, more than 366 days, or a range that would leave existing tasks outside it. Repository: `createProject` (UUID), `updateProject`, `deleteProject` (tombstones the project and its tasks in one transaction, `dirty = 1`), `tasksOf`. Project list: "New project" FAB, per-card Edit/Delete menu, a bottom-sheet editor with the Material date-range picker, and a delete confirmation. A newly created project opens straight away.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL, 67 tests, 0 failed; lint 0 errors, 12 warnings** (versions only; a `PluralsCandidate` warning on the max-days message was fixed by making it a plural). New: `ProjectRulesTest` (5), `ProjectListViewModelTest` (6).
- On device: the sheet defaulted to 11 Nov – 17 Nov 2026 (7 days from today); Create with a blank name showed "Give the project a name."; a 141-day project (11 Nov 2026 – 31 Mar 2027, typed in the picker's text mode) opened straight away; at 300 % Hours zoom (~1.95 M px of content) twelve fast flings reached Sat 14 Nov without a crash, with header, grid and day label consistent; shrinking the sample to 3–4 Mar showed "4 tasks fall outside these dates…" and saved nothing; deleting the 141-day project left only the unchanged sample. Crash buffer empty. `gfxinfo` reported 21 % janky frames over the whole session (debug build, includes cold start and sheet animations) — not treated as a performance measurement.

**AI mistake — keyboard covered the date picker.**
1. *What AI generated:* the date button opened the picker while the name field kept focus.
2. *Why wrong:* the soft keyboard stayed up over the picker's lower half and its OK/Cancel buttons.
3. *How discovered:* on-device screenshot of the picker.
4. *Correction:* the date button clears focus before opening the picker; re-checked on device with `dumpsys input_method` (`mInputShown=false`) and a screenshot.

**Testing note:** pressing Escape to hide the keyboard dismissed the whole bottom sheet (expected sheet behaviour, not a bug); later runs left the keyboard up instead.

### B14 — Task add, edit and delete

- `TaskRules.validate` (domain): blank title, end not after start, shorter than the 15-minute minimum, or outside the project's dates. Repository: `createTask` (next `sort_order`, counting deleted rows so an undone task keeps its row), `deleteTask` (tombstone), and `updateTask` doubles as undo (an upsert clears `deleted`).
- Timeline: "Add task" FAB; editing from the name cell or a pencil in the top bar when a bar is selected; a bottom sheet with title, colour (radio semantics), and start/end date and time pickers (dates limited to the project). Moving the start keeps the duration. New tasks default to 09:00–10:00 today if today is in the project, else on its first day, and are selected after saving. Delete shows a snackbar with Undo. Empty projects show a hint.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL, 76 tests, 0 failed; lint 0 errors, 12 warnings** (versions only). New: `TaskRulesTest` (2), 7 ViewModel tests (defaults, last row + selection, duration-preserving start, validation, edit, delete + undo).
- On device: the sheet defaulted to Mon 2 Mar 09:00–10:00 (today, 11 Nov, is outside the sample project), as predicted; "Launch" was added in the last row at the 09:00 grid line and selected (handles and top-bar pencil shown); tapping its name opened "Edit task"; deleting showed "Task deleted / Undo". Undo on a second delete (Review) restored it at 16:00–17:00 in its row. Crash buffer empty.
- Partly a test-timing issue: the first Undo attempt missed because the short snackbar had closed before the next UI dump; "Launch" therefore stayed deleted, which is the correct behaviour when Undo isn't pressed.
- Interruption: the phone revoked USB debugging authorization mid-check (`adb: device unauthorized`); the user re-allowed it and the check continued.

### B15 — Calendar time model, Weeks and Months

- `TimeScale` gained WEEKS and MONTHS, each with its own zoom limits, minor-tick spacing and snap options (Hours 15m/30m/1h, Days 1h/6h/1d, Weeks 1d, Months 1d/1w). Switching scale keeps the snap only if the new scale offers it. `TimelineConfig.restore` repairs stored values.
- `TimelineMath.ticks(window, zone, scale)` is calendar-based: local hours, local midnights (a DST day is 23/25 h wide), Mondays, and the 1st of each month. `Tick.majorIndex` lets the header thin labels evenly; labels are formatted lazily the first time they are drawn.
- `SnapGrid` replaces the snap interval in `TaskEdits`: `FixedGrid` (sub-day, the old `TimeSnapper` math) and `DayGrid` (whole days following local midnights).
- View settings (scale, zoom, snap, row height) are saved per project in a local-only `view_settings` table: Room schema v2 with `MIGRATION_1_2`. The timeline stays in Loading until they are read, so the first frame is already at the saved scale and zoom.
- The corner day label now binary-searches precomputed local midnights instead of dividing by 24 h (DST-safe, still allocation-free per scroll frame).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL, 87 tests, 0 failed; lint 0 errors, 12 warnings** (versions only). New: `SnapGridTest` (5, including a Berlin DST day and a weekly grid), tick tests for a DST day, Weeks and Months (28- and 31-day months), config tests for per-scale snaps and `restore`, and a ViewModel test that settings are saved and restored.
- On device: installing over the v1 database migrated without a crash and kept the sample project and its 4 tasks; Weeks showed day lines every 96 px (48 dp); extending the sample to 2 Mar – 30 Jun 2026 (121 days) and switching to Months showed Monday labels 2/9/16/23 Mar every 138 px (≈69 dp/week, as computed from 300 dp per 30.44-day month); Months was still selected after `am force-stop` and relaunch. Crash buffer empty.
- Not verified on device: dragging with a whole-day snap (covered by `SnapGridTest`); the uiautomator dump doesn't report segmented-button selection, so the Weeks restore before switching to Months was not confirmed separately.
- Noted for later: in Months scale a project starting mid-month shows no month label until the next 1st (the corner label shows the date).

### B16 — Task dependencies

- Finish-to-start links: `TaskDependency` (domain), `dependencies` table (Room v3, `MIGRATION_2_3`; the migration SQL was compared with the exported v3 schema and matches it exactly), `DependencyRules` (a task can't depend on itself or on anything downstream of it). Repository: `observeDependencies`, `setPredecessors` (adds missing links, tombstones removed ones), `deleteTask` now tombstones the task's links and returns them, `restoreTask` brings both back for undo; deleting a project tombstones its links too.
- Editor: "Depends on (must finish first)" chips for the other tasks; tasks that would close a cycle are disabled, and the choice is re-checked against current links on save. Timeline: `DependencyArrows` draws elbow arrows from predecessor end to successor start in the draw phase (shared scroll + list position; equal row heights let arrows reach off-screen rows). Violated links are red and routed back around the row boundary.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL, 94 tests, 0 failed; lint 0 errors, 12 warnings** (versions only). New: `DependencyRulesTest` (5: chains, branches, duplicates, violation, self-link), two ViewModel tests (save/unlink/forbidden cycle; undo restores links).
- On device: installing over v2 migrated without a crash; linking Implementation ← UI Design (10:30 → 11:00) drew a grey elbow and QA Testing ← Implementation (14:00 → 13:30) drew a red, rerouted arrow, as predicted; dragging QA Testing +52 px at 51 % zoom (≈30 min) gave 14:00–16:30 and the arrow turned grey. Crash buffer empty.
- Not verified on device: undo restoring links (unit-tested only).
- Found while testing: with four scales, the scale buttons push the zoom controls off-screen on a 360 dp phone (the toolbar scrolls horizontally, so they're reachable). Tighter segment padding had no visible effect and was reverted; recorded as a known limitation.
- Arrows follow committed task times, not the live drag preview, so they update when a drag ends.
- Tooling note: one relaunch after `installDebug` left the phone on the launcher (the next `am start` worked).
