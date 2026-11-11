package noel.chronova.app.presentation.components

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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlin.math.max
import noel.chronova.app.R
import noel.chronova.app.core.timeline.TimelineMath
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.presentation.TimeFormat
import noel.chronova.app.presentation.timeline.TimelineUiState

private val NameColumnWidth = 128.dp
private val HeaderHeight = 44.dp
private const val DAY_MILLIS = 24 * 60 * TimelineMath.MILLIS_PER_MINUTE

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
    onCommitEdit: (SchedulerTask) -> Unit,
    onEditTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val config = state.config
    val window = state.window
    val density = LocalDensity.current

    val pxPerMinute = TimelineMath.pxPerMinute(with(density) { config.unitWidthDp.dp.toPx() }, config.timeScale)
    val ticks = remember(window.lengthMillis, config.timeScale) {
        TimelineMath.ticks(window.lengthMillis, config.timeScale)
    }
    val contentWidthPx = TimelineMath.durationToWidth(window.lengthMillis, pxPerMinute)
    val rowHeight = config.rowHeightDp.dp

    // First composition only: start with the earliest task one unit from the left edge.
    val viewport = rememberSaveable(saver = TimelineViewportState.Saver) {
        TimelineViewportState(initialScrollX(state, pxPerMinute))
    }
    val listState = rememberLazyListState()

    val colors = MaterialTheme.colorScheme
    // Time always flows left-to-right, even in RTL locales.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.background(colors.surface)) {
            val viewportWidthPx = max(0, constraints.maxWidth - with(density) { NameColumnWidth.roundToPx() }).toFloat()
            // Keeps the centre time fixed across zoom/scale changes and clamps the scroll.
            SideEffect { viewport.onGeometry(contentWidthPx, viewportWidthPx, pxPerMinute) }

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

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
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
                    if (state.tasks.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tasks_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(start = NameColumnWidth)
                                .padding(24.dp),
                        )
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
                        items(state.tasks, key = { it.id }) { task ->
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
 * visible between midnight labels. derivedStateOf limits recomposition to the
 * moments the day actually changes, not every scrolled pixel.
 */
@Composable
private fun VisibleDayLabel(
    state: TimelineUiState,
    pxPerMinute: Float,
    viewport: TimelineViewportState,
    modifier: Modifier = Modifier,
) {
    // Only the day index is derived per scroll change (no allocation); the label is
    // formatted once per day.
    val dayIndex by remember(state.window.startMillis, pxPerMinute) {
        derivedStateOf {
            val leftEdge = TimelineMath.xToTime(viewport.scrollX, state.window.startMillis, pxPerMinute)
            Math.floorDiv(leftEdge - state.window.startMillis, DAY_MILLIS)
        }
    }
    // +12 h keeps the formatted date correct even if a DST shift moves midnight by an hour.
    val day = remember(dayIndex, state.window.startMillis, state.zone) {
        TimeFormat.day(state.window.startMillis + dayIndex * DAY_MILLIS + DAY_MILLIS / 2, state.zone)
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

private fun Modifier.columnDivider(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
}

private fun Modifier.rowDivider(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
}
