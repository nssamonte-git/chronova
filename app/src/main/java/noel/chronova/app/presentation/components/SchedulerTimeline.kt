package noel.chronova.app.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import noel.chronova.app.core.timeline.TimelineMath
import noel.chronova.app.presentation.ChronovaUiState
import noel.chronova.app.ui.theme.toColor

private val NameColumnWidth = 128.dp
private val HeaderHeight = 44.dp
private val MinBarWidth = 4.dp

/**
 * The scheduler: fixed task-name column, time header, grid and task bars.
 *
 * Scrolling: one horizontal [ScrollState] is owned by the body's `horizontalScroll`;
 * the header only reads it while drawing. One vertical scroll wraps names and bars
 * together, so rows can never fall out of alignment.
 */
@Composable
fun SchedulerTimeline(
    state: ChronovaUiState,
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
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(HeaderHeight)) {
                    Box(
                        modifier = Modifier
                            .width(NameColumnWidth)
                            .fillMaxHeight()
                            .columnDivider(colors.outlineVariant)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "Tasks",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
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
                        Box(Modifier.width(totalWidth).heightIn(min = bodyMinHeight)) {
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
                                        val x = TimelineMath.timeToX(task.startMillis, window.startMillis, pxPerMinute)
                                        val width = with(density) {
                                            TimelineMath.durationToWidth(task.durationMillis, pxPerMinute).toDp()
                                        }.coerceAtLeast(MinBarWidth)
                                        Box(Modifier.fillMaxWidth().height(rowHeight)) {
                                            TaskBar(
                                                title = task.title,
                                                color = task.color.toColor(),
                                                // Layout-phase offset: moving a bar won't re-measure siblings.
                                                modifier = Modifier
                                                    .offset { IntOffset(x.roundToInt(), 0) }
                                                    .width(width),
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
}

/** On first composition only, scroll so the earliest task starts one unit in. */
@Composable
private fun InitialScrollToFirstTask(
    state: ChronovaUiState,
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
