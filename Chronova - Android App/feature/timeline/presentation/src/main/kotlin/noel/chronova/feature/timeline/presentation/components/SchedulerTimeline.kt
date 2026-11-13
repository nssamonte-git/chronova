package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import noel.chronova.core.timeline.TaskVisibility
import noel.chronova.core.timeline.TimelineMath
import noel.chronova.core.ui.TimeFormat
import noel.chronova.feature.timeline.presentation.R
import noel.chronova.feature.timeline.presentation.TaskUi
import noel.chronova.feature.timeline.presentation.TimelineUiState

private val NameColumnWidth = 128.dp
private val HeaderHeight = 44.dp

/**
 * The scheduler: fixed task-name column, time header, grid and task bars.
 *
 * Horizontal: one [TimelineViewportState] is the only scroll position. The header,
 * the grid and every bar read it in their draw or layout phase, so content of any
 * length is never measured at full width.
 *
 * Vertical: rows are a [LazyColumn] whose items hold both the name cell and the
 * bar lane, so names and bars can't fall out of alignment and long task lists are
 * virtualized.
 */
@Composable
fun SchedulerTimeline(
    state: TimelineUiState,
    onSelectTask: (String?) -> Unit,
    onCommitEdit: (original: TaskUi, edited: TaskUi) -> Unit,
    onEditTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** Tasks the assistant just changed: outlined, and the first one scrolled into view. */
    highlighted: Set<String> = emptySet(),
    /** A pinch step on the grid: above 1 zooms in (fingers spreading), below 1 zooms out. */
    onZoomBy: (factor: Float) -> Unit = {},
    /** The fingers of a pinch lifted. */
    onZoomEnd: () -> Unit = {},
) {
    val config = state.config
    val window = state.window
    val density = LocalDensity.current

    val pxPerMinute = TimelineMath.pxPerMinute(with(density) { config.unitWidthDp.dp.toPx() }, config.timeScale)
    val ticks = remember(window, state.zone, config.timeScale) {
        TimelineMath.ticks(window, state.zone, config.timeScale)
    }
    val contentWidthPx = TimelineMath.durationToWidth(window.lengthMillis, pxPerMinute)
    val rowHeight = config.rowHeightDp.dp

    // First composition only: start with the earliest task one unit from the left edge.
    val viewport = rememberSaveable(saver = TimelineViewportState.Saver) {
        TimelineViewportState(initialScrollX(state, pxPerMinute))
    }
    val listState = rememberLazyListState()
    val currentOnZoomBy by rememberUpdatedState(onZoomBy)
    val currentOnZoomEnd by rememberUpdatedState(onZoomEnd)

    val colors = MaterialTheme.colorScheme
    // Time always flows left-to-right, even in RTL locales.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.background(colors.surface)) {
            val viewportWidthPx = max(0, constraints.maxWidth - with(density) { NameColumnWidth.roundToPx() }).toFloat()
            // Keeps the centre time fixed across zoom/scale changes and clamps the scroll.
            SideEffect { viewport.onGeometry(contentWidthPx, viewportWidthPx, pxPerMinute) }

            // Rows list only the tasks on screen, plus the selected one (a drag selects its bar, so a
            // dragged task never loses its row) and any the assistant just changed. derivedStateOf
            // recomposes the rows only when that list changes, not on every scroll frame.
            val keep = remember(state.selectedTaskId, highlighted) { highlighted + listOfNotNull(state.selectedTaskId) }
            // A milestone has no length; it counts as on screen while its diamond or title is.
            val milestoneBefore = with(density) { (MilestoneTouchWidth / 2).toPx() }
            val milestoneAfter = milestoneBefore + with(density) { MilestoneLabelWidth.toPx() }
            val shownTasks by remember(state.tasks, keep, pxPerMinute, window.startMillis, viewportWidthPx) {
                derivedStateOf {
                    val visible = TaskVisibility.visibleSpan(viewport.scrollX, viewportWidthPx, window.startMillis, pxPerMinute)
                    TaskVisibility.onScreen(
                        state.tasks, visible, keep,
                        id = { it.id },
                        start = { if (it.isMilestone) it.startMillis - TimelineMath.pxToMillis(milestoneBefore, pxPerMinute) else it.startMillis },
                        end = { if (it.isMilestone) it.endMillis + TimelineMath.pxToMillis(milestoneAfter, pxPerMinute) else it.endMillis },
                    )
                }
            }

            // Bring the first task the assistant changed into view (row, then time) while it says what changed.
            // Keyed on its times too: the highlight can arrive before the moved task does, and the scroll
            // must follow the task to where it now is.
            val highlightTask = state.tasks.firstOrNull { it.id in highlighted }
            LaunchedEffect(highlighted, highlightTask?.startMillis, highlightTask?.endMillis) {
                val task = highlightTask ?: return@LaunchedEffect
                // The highlighted task is always listed (it is kept), so its row exists.
                val row = shownTasks.indexOfFirst { it.id == task.id }
                val visibleRows = listState.layoutInfo.visibleItemsInfo.map { it.index }
                if (row >= 0 && row !in visibleRows) listState.animateScrollToItem(row)
                val startX = (task.startMillis - window.startMillis) / 60_000f * pxPerMinute
                val endX = (task.endMillis - window.startMillis) / 60_000f * pxPerMinute
                val width = viewport.viewportWidth
                if (width > 0f && (endX < viewport.scrollX || startX > viewport.scrollX + width)) {
                    viewport.animateScrollTo(startX - width * 0.2f)
                }
            }

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(HeaderHeight)) {
                    VisibleDayLabel(
                        state = state,
                        pxPerMinute = pxPerMinute,
                        viewport = viewport,
                        modifier = Modifier
                            .width(NameColumnWidth)
                            .fillMaxHeight()
                            .columnDivider(colors.outlineVariant)
                            .padding(horizontal = 12.dp),
                    )
                    TimelineHeader(
                        originMillis = window.startMillis,
                        zone = state.zone,
                        scale = config.timeScale,
                        ticks = ticks,
                        pxPerMinute = pxPerMinute,
                        viewport = viewport,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                val nameColumnPx = with(density) { NameColumnWidth.toPx() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // Pinch to zoom, keeping the time between the fingers where it is. Before
                        // the scroll so its Initial-pass listener sees two-finger moves first.
                        .pointerInput(nameColumnPx) {
                            detectPinch(
                                onZoom = { factor, focusX ->
                                    viewport.zoomAnchor = (focusX - nameColumnPx).coerceAtLeast(0f)
                                    currentOnZoomBy(factor)
                                },
                                onEnd = {
                                    viewport.zoomAnchor = null
                                    currentOnZoomEnd()
                                },
                            )
                        }
                        .scrollable(viewport.scrollableState, Orientation.Horizontal, reverseDirection = true)
                        // Taps on empty timeline space clear the selection; bars consume their own taps.
                        .pointerInput(Unit) { detectTapGestures { onSelectTask(null) } },
                ) {
                    TimelineGrid(
                        ticks = ticks,
                        pxPerMinute = pxPerMinute,
                        viewport = viewport,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = NameColumnWidth),
                    )
                    // The name column's divider runs the full height, below the last row too.
                    Box(
                        Modifier
                            .width(NameColumnWidth)
                            .fillMaxHeight()
                            .columnDivider(colors.outlineVariant),
                    )
                    if (shownTasks.isEmpty()) {
                        Text(
                            text = stringResource(if (state.tasks.isEmpty()) R.string.tasks_empty else R.string.tasks_none_here),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(start = NameColumnWidth)
                                .padding(24.dp),
                        )
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
                        items(shownTasks, key = { it.id }) { task ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .rowDivider(colors.outlineVariant.copy(alpha = 0.5f)),
                            ) {
                                TaskNameCell(
                                    task = task,
                                    zone = state.zone,
                                    rowHeight = rowHeight,
                                    onClick = { onEditTask(task.id) },
                                    modifier = Modifier
                                        .width(NameColumnWidth)
                                        .fillMaxHeight(),
                                )
                                TaskLane(
                                    task = task,
                                    selected = task.id == state.selectedTaskId,
                                    highlighted = task.id in highlighted,
                                    window = window,
                                    zone = state.zone,
                                    pxPerMinute = pxPerMinute,
                                    snapMinutes = config.snapMinutes,
                                    viewport = viewport,
                                    onSelect = onSelectTask,
                                    onCommit = onCommitEdit,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    }
                    DependencyArrows(
                        tasks = shownTasks,
                        dependencies = state.dependencies,
                        originMillis = window.startMillis,
                        pxPerMinute = pxPerMinute,
                        rowHeight = rowHeight,
                        viewport = viewport,
                        listState = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = NameColumnWidth),
                    )
                }
            }
        }
    }
}

/** The earliest task one scale unit in from the left edge (clamped once laid out). */
private fun initialScrollX(state: TimelineUiState, pxPerMinute: Float): Float {
    val first = state.tasks.minOfOrNull { it.startMillis } ?: return 0f
    val unitPx = pxPerMinute * state.config.timeScale.minutes
    return max(0f, TimelineMath.timeToX(first, state.window.startMillis, pxPerMinute) - unitPx)
}

/**
 * Corner cell naming the day at the left edge of the viewport, so the date stays
 * visible between midnight labels. The local midnights are computed once per
 * window, and a binary search over them is all that runs per scroll change;
 * derivedStateOf limits recomposition to the moments the day actually changes.
 */
@Composable
private fun VisibleDayLabel(
    state: TimelineUiState,
    pxPerMinute: Float,
    viewport: TimelineViewportState,
    modifier: Modifier = Modifier,
) {
    val window = state.window
    val dayStarts = remember(window, state.zone) { localMidnightOffsets(window.startMillis, window.endMillis, state.zone) }
    val dayIndex by remember(dayStarts, pxPerMinute) {
        derivedStateOf {
            val leftEdge = TimelineMath.pxToMillis(viewport.scrollX, pxPerMinute)
            lastAtOrBefore(dayStarts, leftEdge)
        }
    }
    val day = remember(dayIndex, dayStarts, state.zone) {
        TimeFormat.day(window.startMillis + dayStarts[dayIndex], state.zone)
    }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            text = day,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Offsets from [startMillis] of each local midnight in the window (the first is 0). */
private fun localMidnightOffsets(startMillis: Long, endMillis: Long, zone: ZoneId): LongArray {
    val first = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val offsets = ArrayList<Long>()
    var day = first
    while (true) {
        val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
        if (midnight >= endMillis) break
        offsets += midnight - startMillis
        day = day.plusDays(1)
    }
    return offsets.toLongArray()
}

/** Index of the last value <= [value] in the ascending [sorted] array (0 if none). */
private fun lastAtOrBefore(sorted: LongArray, value: Long): Int {
    var low = 0
    var high = sorted.size - 1
    var found = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (sorted[mid] <= value) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

private fun Modifier.columnDivider(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
}

private fun Modifier.rowDivider(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
}
