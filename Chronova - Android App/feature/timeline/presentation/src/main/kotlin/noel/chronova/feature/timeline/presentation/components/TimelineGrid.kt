package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import noel.chronova.core.designsystem.ChronovaTheme
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
    /** Saturdays and Sundays as [start, end) offsets from the window start, ascending; shaded behind the lines. */
    weekends: List<LongRange> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    val extended = ChronovaTheme.extended
    val minorColor = colors.outlineVariant.copy(alpha = 0.6f)
    val weekendShade = extended.weekendShade
    val aurora = extended.aurora
    val glowAlpha = extended.glowAlpha
    Canvas(modifier.clipToBounds()) {
        val stroke = 1.dp.toPx()
        val scrollX = viewport.scrollX
        val visible = TimelineMath.visibleTicks(
            ticks,
            fromOffsetMillis = TimelineMath.pxToMillis(scrollX - stroke, pxPerMinute),
            toOffsetMillis = TimelineMath.pxToMillis(scrollX + size.width + stroke, pxPerMinute),
        )
        for (weekend in weekends) {
            val left = TimelineMath.durationToWidth(weekend.first, pxPerMinute) - scrollX
            val right = TimelineMath.durationToWidth(weekend.last, pxPerMinute) - scrollX
            if (right < 0f) continue
            if (left > size.width) break
            drawRect(weekendShade, topLeft = Offset(left.coerceAtLeast(0f), 0f), size = Size(right.coerceAtMost(size.width) - left.coerceAtLeast(0f), size.height))
        }
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
                // Now: the logo's gradient from top to bottom, over a soft glow of the same colours.
                drawLine(
                    Brush.verticalGradient(aurora.map { it.copy(alpha = glowAlpha) }),
                    Offset(x, 0f),
                    Offset(x, size.height),
                    strokeWidth = 8 * stroke,
                )
                drawLine(Brush.verticalGradient(aurora), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2 * stroke)
            }
        }
    }
}
