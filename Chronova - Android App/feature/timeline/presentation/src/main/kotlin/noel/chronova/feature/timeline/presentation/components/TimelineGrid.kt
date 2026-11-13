package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import noel.chronova.core.timeline.Tick
import noel.chronova.core.timeline.TimelineMath

/**
 * Vertical time grid behind the rows. Uses the same [ticks], conversion and scroll
 * position as the header, so lines and labels always coincide. Only visible ticks
 * are drawn, and the scroll is read in the draw phase (no recomposition on scroll).
 */
@Composable
fun TimelineGrid(
    ticks: List<Tick>,
    pxPerMinute: Float,
    viewport: TimelineViewportState,
    modifier: Modifier = Modifier,
    /** Now, as an offset from the window start; null when now is outside the project. Drawn as a line. */
    nowOffsetMillis: Long? = null,
) {
    val colors = MaterialTheme.colorScheme
    val minorColor = colors.outlineVariant.copy(alpha = 0.5f)
    val nowColor = colors.primary
    Canvas(modifier.clipToBounds()) {
        val stroke = 1.dp.toPx()
        val scrollX = viewport.scrollX
        val visible = TimelineMath.visibleTicks(
            ticks,
            fromOffsetMillis = TimelineMath.pxToMillis(scrollX - stroke, pxPerMinute),
            toOffsetMillis = TimelineMath.pxToMillis(scrollX + size.width + stroke, pxPerMinute),
        )
        for (index in visible) {
            val tick = ticks[index]
            val x = TimelineMath.durationToWidth(tick.offsetMillis, pxPerMinute) - scrollX
            drawLine(
                color = if (tick.isMajor) colors.outlineVariant else minorColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = stroke,
            )
        }
        if (nowOffsetMillis != null) {
            val x = TimelineMath.durationToWidth(nowOffsetMillis, pxPerMinute) - scrollX
            if (x in -stroke..size.width + stroke) {
                drawLine(nowColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2 * stroke)
            }
        }
    }
}
