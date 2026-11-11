package noel.chronova.app.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import noel.chronova.app.core.timeline.TimelineMath
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.presentation.timeline.TimelineUiState
import noel.chronova.app.presentation.TimeFormat

private val NameColumnWidth = 128.dp
private val HeaderHeight = 44.dp
private const val DAY_MILLIS = 24 * 60 * TimelineMath.MILLIS_PER_MINUTE

/**
 * The scheduler: fixed task-name column, time header, grid and task bars.
 *
 * Scrolling: one horizontal [ScrollState] is owned by the body's `horizontalScroll`;
 * the header only reads it while drawing. One vertical scroll wraps names and bars
 * together, so rows can never fall out of alignment.
 */
@Composable
fun SchedulerTimeline(
    state: TimelineUiState,
    onSelectTask: (String?) -> Unit,
    onCommitEdit: (SchedulerTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = state.config
    val window = state.window
    val density = LocalDensity.current

    val pxPerMinute = TimelineMath.pxPerMinute(with(density) { config.unitWidthDp.dp.toPx() }, config.timeScale)
    val ticks = remember(window.lengthMillis, config.timeScale) {
        TimelineMath.ticks(window.lengthMillis, config.timeScale)
    }
    val totalWidth = with(density) { TimelineMath.durationToWidth(window.lengthMillis, pxPerMinute).toDp() }
    val rowHeight = config.rowHeightDp.dp

    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    InitialScrollToFirstTask(state, pxPerMinute, horizontalScroll)

    val colors = MaterialTheme.colorScheme
    // Time always flows left-to-right, even in RTL locales.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.background(colors.surface)) {
            val bodyMinHeight = maxHeight - HeaderHeight
            val viewportWidthPx = constraints.maxWidth - with(density) { NameColumnWidth.roundToPx() }
            KeepCenterTimeOnZoom(pxPerMinute, viewportWidthPx, horizontalScroll)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(HeaderHeight)) {
                    VisibleDayLabel(
                        state = state,
                        pxPerMinute = pxPerMinute,
                        scrollState = horizontalScroll,
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
                        scrollState = horizontalScroll,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(verticalScroll),
                ) {
                    TaskNameColumn(
                        tasks = state.tasks,
                        zone = state.zone,
                        rowHeight = rowHeight,
                        modifier = Modifier
                            .width(NameColumnWidth)
                            .heightIn(min = bodyMinHeight)
                            .columnDivider(colors.outlineVariant),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScroll),
                    ) {
                        Box(
                            Modifier
                                .width(totalWidth)
                                .heightIn(min = bodyMinHeight)
                                // Taps on empty timeline space clear the selection; bars consume their own taps.
                                .pointerInput(Unit) { detectTapGestures { onSelectTask(null) } },
                        ) {
                            TimelineGrid(
                                ticks = ticks,
                                pxPerMinute = pxPerMinute,
                                rowCount = state.tasks.size,
                                rowHeight = rowHeight,
                                modifier = Modifier.matchParentSize(),
                            )
                            Column {
                                state.tasks.forEach { task ->
                                    key(task.id) {
                                        TaskRow(
                                            task = task,
                                            selected = task.id == state.selectedTaskId,
                                            window = window,
                                            zone = state.zone,
                                            pxPerMinute = pxPerMinute,
                                            snapMinutes = config.snapMinutes,
                                            rowHeight = rowHeight,
                                            onSelect = onSelectTask,
                                            onCommit = onCommitEdit,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    // Only the day index is derived per scroll change (no allocation); the label is
    // formatted once per day.
    val dayIndex by remember(state.window.startMillis, pxPerMinute) {
        derivedStateOf {
            val leftEdge = TimelineMath.xToTime(scrollState.value.toFloat(), state.window.startMillis, pxPerMinute)
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

private class ZoomAnchor(var pxPerMinute: Float, var centerMinutes: Float? = null)

/**
 * Keeps the time at the centre of the viewport fixed when zoom or time scale
 * changes (otherwise content would scale around the left edge).
 *
 * The centre time must be read with the *old* scale, before the new layout clamps
 * the scroll position, so it is captured during composition — without subscribing
 * to scroll changes — and applied one frame later, once the content has been
 * re-measured at its new width.
 */
@Composable
private fun KeepCenterTimeOnZoom(
    pxPerMinute: Float,
    viewportWidthPx: Int,
    scrollState: ScrollState,
) {
    val anchor = remember { ZoomAnchor(pxPerMinute) }
    if (anchor.pxPerMinute != pxPerMinute) {
        // Read once, unobserved, only when the scale changes; lint can't see that this
        // doesn't subscribe the composition to every scroll pixel.
        @Suppress("FrequentlyChangingValue")
        val scrollX = Snapshot.withoutReadObservation { scrollState.value }
        anchor.centerMinutes = (scrollX + viewportWidthPx / 2f) / anchor.pxPerMinute
        anchor.pxPerMinute = pxPerMinute
    }
    LaunchedEffect(pxPerMinute) {
        val centerMinutes = anchor.centerMinutes ?: return@LaunchedEffect
        anchor.centerMinutes = null
        withFrameNanos { } // wait for the layout at the new content width
        scrollState.scrollTo((centerMinutes * pxPerMinute - viewportWidthPx / 2f).roundToInt().coerceAtLeast(0))
    }
}

/** On first composition only, scroll so the earliest task starts one unit in. */
@Composable
private fun InitialScrollToFirstTask(
    state: TimelineUiState,
    pxPerMinute: Float,
    scrollState: ScrollState,
) {
    var done by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        val first = state.tasks.minOfOrNull { it.startMillis } ?: return@LaunchedEffect
        val unitPx = pxPerMinute * state.config.timeScale.minutes
        val x = TimelineMath.timeToX(first, state.window.startMillis, pxPerMinute) - unitPx
        scrollState.scrollTo(x.roundToInt().coerceAtLeast(0))
        done = true
    }
}

private fun Modifier.columnDivider(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
}
