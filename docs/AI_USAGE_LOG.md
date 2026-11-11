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
