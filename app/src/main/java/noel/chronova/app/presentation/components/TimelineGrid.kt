package noel.chronova.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import noel.chronova.app.core.timeline.Tick
import noel.chronova.app.core.timeline.TimelineMath

/**
 * Vertical time grid and row separators behind the task bars. Uses the same
 * [ticks] and conversion as the header, so lines and labels always coincide.
 */
@Composable
fun TimelineGrid(
    ticks: List<Tick>,
    pxPerMinute: Float,
    rowCount: Int,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val minorColor = colors.outlineVariant.copy(alpha = 0.5f)
    Canvas(modifier) {
        val stroke = 1.dp.toPx()
        ticks.forEach { tick ->
            val x = TimelineMath.durationToWidth(tick.offsetMillis, pxPerMinute)
            drawLine(
                color = if (tick.isMajor) colors.outlineVariant else minorColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = stroke,
            )
        }
        // Rows are laid out at whole-pixel heights; match that so separators line up.
        val rowPx = rowHeight.roundToPx().toFloat()
        for (row in 1..rowCount) {
            val y = row * rowPx - stroke / 2
            drawLine(minorColor, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}
