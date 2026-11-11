# Chronova — Shape Your Time

Chronova is a native Android timeline (Gantt-style) scheduler built with Kotlin and Jetpack Compose. Tasks are shown as bars on a horizontal time axis. You can drag a bar to reschedule it, resize it from either edge, and snap edits to a configurable grid.

It was built for the PicCollage take-home assignment. No third-party timeline or Gantt libraries are used.

## Features

| Requirement | Status | Where |
|---|---|---|
| F01 Timeline header with readable labels and grid lines, hours or days | Done | `TimelineHeader`, `TimelineGrid`, `TimelineMath.ticks` |
| F02 Horizontal scrolling with header and content in sync, fixed name column | Done | `SchedulerTimeline` (one `TimelineViewportState`) |
| F03 Tasks as bars in separate rows, positioned from the time scale | Done | `TaskLane`, `TaskBar`, `TimelineMath` |
| F04 Drag to move (keeps duration) | Done | `detectTaskGestures`, `TaskEdits.move` |
| F05 Resize from the left (keeps end) | Done | `HandleHitTest`, `TaskEdits.resizeStart` |
| F06 Resize from the right (keeps start) | Done | `TaskEdits.resizeEnd` |
| F07 Snapping 15 / 30 / 60 min, both directions, no cumulative error | Done | `TimeSnapper`, `TaskEdits` |
| F08 Configuration: time unit, zoom, snap, row height | Done | `TimelineControls`, `TimelineConfig` |
| F09 Sample tasks on launch | Done | `SampleTasks` |

Also included:
- Selection highlight with resize grips.
- A live start–end label while dragging.
- A haptic tick on each snap step.
- A corner label showing the day currently in view.
- Zoom and scale changes keep the centre time fixed.
- A settings toggle in the top bar that hides the toolbar.

## Running

Requirements: Android Studio with AGP 9.5 support, JDK 17 or newer (Gradle's toolchain resolver can download JDK 25 for the daemon), and Android SDK 37.

```bash
./gradlew :app:installDebug          # build and install on a connected device/emulator
./gradlew :app:testDebugUnitTest     # 39 JVM unit tests
./gradlew :app:lintDebug             # Android lint
```

- `minSdk` 26, `targetSdk`/`compileSdk` 37.
- Single `app` module.
- Dependencies: Compose BOM 2026.04.01 (Material 3, material-icons-core), activity-compose, and the lifecycle Compose artifacts. Nothing else.

## Using it

- **Scroll:** swipe the empty timeline or the header to scroll horizontally, and swipe vertically to see more rows.
- **Select:** tap a bar to select it. It gets an outline and a grip on each edge. Tap empty space to deselect.
- **Move:** drag any bar. It follows your finger, shows its snapped start–end time, and is saved when you lift your finger.
- **Resize:** drag a selected bar's grip. The left grip changes the start time and the right grip changes the end time.
- **Settings:** use the toolbar (gear icon) to switch Hours/Days, zoom in or out (or reset), pick the snap interval, and change the row height.

## Architecture

The app has a single module. It uses package boundaries rather than Gradle modules and has no DI framework.

```
noel.chronova.app
├── MainActivity.kt                 edge-to-edge host for ChronovaScreen
├── core/timeline                   pure Kotlin, no Android dependencies
│   ├── TimelineMath.kt             time <-> x, duration -> width, tick generation
│   ├── TimeSnapper.kt              floorDiv-based nearest-interval snapping
│   ├── TaskEdits.kt                move / resizeStart / resizeEnd from gesture-start values
│   ├── HandleHitTest.kt            which GestureMode a pointer-down selects
│   └── GestureMode.kt              MOVE, RESIZE_START, RESIZE_END
├── domain
│   ├── SampleTasks.kt              the four sample tasks and the 3-day window
│   ├── model                       Project, SchedulerTask, TimelineConfig/TimeScale, TimelineWindow
│   └── repository                  ProjectRepository (interface)
├── data
│   ├── local                       Room database, entities, DAOs, mappers
│   └── repository                  RoomProjectRepository
├── navigation                      ChronovaNavHost (project list → project timeline)
├── presentation
│   ├── projects                    ProjectListScreen / ViewModel
│   ├── timeline                    ProjectTimelineScreen / ViewModel, TimelineUiState
│   ├── TimeFormat.kt
│   └── components                  SchedulerTimeline, TimelineViewportState, TimelineHeader, TimelineGrid,
│                                   TaskNameCell, TaskLane, TaskBar, TaskGestures, TimelineControls
└── ui/theme                        Material 3 colour scheme from the Chronova palette
```

**State flow:**
- The ViewModel is the single source of truth for tasks, configuration and selection. It exposes one `StateFlow`, and the screen collects it with `collectAsStateWithLifecycle()`.
- Data flows one way: the UI sends intents, and the ViewModel produces a new immutable state.
- Transient drag state (the preview of the bar being edited) stays local to `TaskLane`. Only the finished edit is sent to the ViewModel.

### Coordinate system

There is a single origin: the start of the timeline window (local midnight of the reference day). Every on-screen position is derived from time, and time is never stored as pixels.

```
pxPerMinute = unitWidthPx / minutesPerUnit        (unit = hour or day; unitWidth = zoom)
x           = (time − origin) in minutes × pxPerMinute
width       = duration in minutes × pxPerMinute
time        = origin + round(x / pxPerMinute) minutes
```

- Pixel → time conversions round to whole minutes. Positions are `Float`s, which can't represent millisecond-exact values at large x, and one minute is the timeline's real resolution.
- Changing zoom, scale or row height only changes `pxPerMinute` or the layout. Task timestamps are untouched, and there's a test for this.

### Scrolling

- **One authoritative horizontal scroll position.** `TimelineViewportState` holds it; the body and the header both feed it through `Modifier.scrollable(…, reverseDirection = true)`.
- **Nothing is laid out at full content width.** A long project at high zoom is wider than any Compose layout can measure, so the header, grid and bars read the scroll position in their draw or layout phase and handle only what is near the viewport. Scrolling therefore only redraws or re-places (no recomposition), and the labels can't drift from the grid.
- **Bars are clipped to a compose span**: the viewport-wide page around the scroll position, plus one viewport on each side. It changes only when the scroll crosses a page, so rows don't recompose every frame.
- **Rows are a `LazyColumn`.** Each item holds both the name cell and the bar lane, so they can't misalign, and long task lists are virtualized.
- **Zoom keeps the centre time** by moving the scroll in a `SideEffect`, before the frame is laid out and drawn.
- **Shared ticks:** the header and grid draw from the same `TimelineMath.ticks` list with the same conversion.
- **One vertical scroll** wraps both the name column and the rows, so rows always stay aligned.
- **Left-to-right always:** the timeline is forced to LTR so time runs left to right in RTL locales too.

### Gestures

- **One handler per bar.** Each bar has a single `pointerInput` (`detectTaskGestures`), keyed on the task id, that decides between a tap and a drag. The drag type (`GestureMode`) is chosen once, at pointer-down, by `HandleHitTest`, so move and resize can never fire together.
- **Resize grips.** Only the selected bar has resize zones: 20 dp outside each edge plus up to 20 dp inside. On narrow bars the inner part shrinks so the middle third always moves.
- **Scroll vs drag.** A drag starts only after horizontal touch slop, and that event is consumed so the parent `scrollable` doesn't also scroll. Vertical movement isn't consumed, so vertical scrolling still works over bars. Swipes on empty space scroll normally.
- **Edits from the gesture start.** Every pointer event recomputes the bar from the task as it was at gesture start plus the total finger distance since pointer-down. Snapping each small step instead would get stuck, because a slow drag moves less than half a snap interval per event. A unit test demonstrates this.
- **Commit and cancel.** Release commits the edit. A cancelled gesture discards the preview. The preview is held until the committed task arrives, which avoids a one-frame flash.
- **Stale values.** `rememberUpdatedState` keeps the long-lived gesture coroutine reading current values (config, task, callbacks) without restarting mid-drag.

### Snapping rules

- **Nearest interval.** Edits round to the nearest multiple of the snap interval, measured from local midnight. Ties round toward later times. `Math.floorDiv` is used so values before the origin round the same way as values after it.
- **Move** snaps the start and keeps the duration.
- **Resize** snaps the edge being dragged. The opposite edge never moves.
- **Minimum duration** is 15 minutes, independent of the snap interval. If a snapped edge would violate it, the edge stops at the nearest grid line that still allows 15 minutes. For example, a task starting at 13:30 with 60-minute snap can shrink to 13:30–14:00.
- **Window clamp:** tasks stay inside the 3-day window. A move that reaches the window end stops at the last grid line where the whole task still fits.
- **Clamped edges stay on the grid.** Limits are rounded inward to a grid line, so a dragged edge never lands between grid lines.

## Testing

`./gradlew :app:testDebugUnitTest` runs 56 JVM tests (0 failures in the last run).

| Required scenario | Test(s) |
|---|---|
| Timestamp → pixel | `TimelineMathTest.timeToX_*` |
| Pixel → time | `TimelineMathTest.xToTime_inverseOfTimeToX`, `pxToMillis_signedDragDistance` |
| Positive and negative snapping | `TimeSnapperTest` (nearest, symmetric negatives, ties, origin-relative, floor/ceil) |
| Moving earlier and later | `TaskEditsTest.move_later_*`, `move_earlier_*`, `move_clampsToWindow`, `move_clampAtWindowEnd*` |
| Left resize keeps end | `TaskEditsTest.resizeStart_preservesEnd` |
| Right resize keeps start | `TaskEditsTest.resizeEnd_preservesStart` |
| Minimum duration | `TaskEditsTest.resize*_cannot*`, `minimumDurationHoldsEvenWithCoarseSnap`, `resizeStart_minimumDurationStopsOnGridWithCoarseSnap` |
| Zoom keeps timestamps | `TimelineMathTest.zoomChange_preservesTimestamps`, `ChronovaViewModelTest.updateConfig_neverChangesTaskTimestamps` |

Also covered:
- Tick generation
- Cumulative-rounding avoidance
- Handle hit zones (`HandleHitTestTest`)
- Config limits and steps (`TimelineConfigTest`)
- ViewModel intents

**Manual verification:** done on a physical phone (Android 10, 720×1600, 360 dp wide) with screenshots, after each feature batch:
- Bars on their grid lines.
- Header and body in sync, including both ends of the range.
- Rotation keeps scroll position and state.
- Drag later and earlier.
- Both resizes and the minimum duration.
- 60-minute snapping.
- Zoom keeps the centre time.
- Days scale, row height, and the toolbar toggle.

Each check's expected values were calculated before running it. The full record is in [`docs/AI_USAGE_LOG.md`](docs/AI_USAGE_LOG.md).

## Decisions and trade-offs

- **minSdk 26** (from the template's 24) so `java.time` works without core-library desugaring.
- **Fixed 3-day window.** The window is the reference day plus two more, with tasks clamped inside it. An unbounded timeline would need virtualised tick drawing and isn't needed for this scope.
- **Fixed 15-minute minimum duration.** A per-snap minimum would make a 13:30 task impossible to keep at 60-minute snap. Instead, the dragged edge stops at the grid line nearest the 15-minute limit.
- **No persistence.** Tasks live in the ViewModel. They survive rotation but reset after process death. Room was explicitly out of scope.
- **Resize only on the selected bar.** This prevents accidental resizes while still giving large touch targets.
- **Pinned dependency versions.** Lint reports newer versions (Compose BOM 2026.09, lifecycle 2.11, Kotlin 2.4, Gradle 9.9). The project stays on the versions it was built and verified with.

## Known limitations

- Dragging a handle past the edge of the viewport doesn't auto-scroll. Scroll, then keep resizing.
- At low zoom, very short tasks (15 minutes) are narrow, so their title is hidden behind the grips when selected.
- Light theme only. The optional enhancements (dark theme, current-time line, animated selection) weren't implemented.
- No instrumented or UI tests. Gesture behaviour was verified manually on a device.

## AI usage

This project was built with Claude Code. [`docs/AI_USAGE_LOG.md`](docs/AI_USAGE_LOG.md) is the per-batch development log, and [`docs/AI_USAGE_WRITEUP.md`](docs/AI_USAGE_WRITEUP.md) is the summary prepared for the shared Google Doc.
