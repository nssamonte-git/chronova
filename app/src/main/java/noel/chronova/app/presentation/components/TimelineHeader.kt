package noel.chronova.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max
import noel.chronova.app.core.timeline.Tick
import noel.chronova.app.core.timeline.TimelineMath
import noel.chronova.app.domain.model.TimeScale
import noel.chronova.app.presentation.TimeFormat

/**
 * Time-axis header. It is not a scroll container: it reads the body's horizontal
 * [scrollState] inside the draw lambda, so scrolling only re-draws this canvas
 * (no recomposition) and the labels can never drift from the grid below.
 *
 * Swipes on the header feed the same [scrollState] through `scrollable`, so there is
 * still exactly one scroll position. `reverseDirection = true` matches how
 * `horizontalScroll` maps an LTR drag (drag left -> later times).
 */
@Composable
fun TimelineHeader(
    originMillis: Long,
    zone: ZoneId,
    scale: TimeScale,
    ticks: List<Tick>,
    pxPerMinute: Float,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val clockStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant)
    val dayStyle = clockStyle.copy(color = colors.onSurface, fontWeight = FontWeight.SemiBold)

    // Labels depend only on time, never on zoom, so they are cached per tick set.
    val labels = remember(ticks, originMillis, zone, scale) {
        ticks.map { tick ->
            if (!tick.isMajor) return@map null
            val millis = originMillis + tick.offsetMillis
            val isMidnight = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().toSecondOfDay() == 0
            if (scale == TimeScale.DAYS || isMidnight) HeaderLabel(TimeFormat.day(millis, zone), isDay = true)
            else HeaderLabel(TimeFormat.clock(millis, zone), isDay = false)
        }
    }

    // Skip labels when units are too narrow for them (low zoom).
    val density = LocalDensity.current
    val unitWidthPx = pxPerMinute * scale.minutes
    val labelStride = max(1, ceil(with(density) { MIN_LABEL_SPACING.toPx() } / unitWidthPx).toInt())
    val unitMillis = scale.minutes * TimelineMath.MILLIS_PER_MINUTE

    Canvas(
        modifier
            .clipToBounds()
            .scrollable(scrollState, Orientation.Horizontal, reverseDirection = true),
    ) {
        val scrollX = scrollState.value.toFloat() // draw-phase read: no recomposition on scroll
        val labelPadding = 6.dp.toPx()
        val cullMargin = MAX_LABEL_WIDTH.toPx()

        ticks.forEachIndexed { index, tick ->
            val x = TimelineMath.durationToWidth(tick.offsetMillis, pxPerMinute) - scrollX
            if (x < -cullMargin || x > size.width) return@forEachIndexed

            val lineTop = if (tick.isMajor) size.height * 0.55f else size.height * 0.75f
            drawLine(
                color = if (tick.isMajor) colors.outline else colors.outlineVariant,
                start = Offset(x, lineTop),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx(),
            )

            val label = labels[index] ?: return@forEachIndexed
            if ((tick.offsetMillis / unitMillis) % labelStride != 0L) return@forEachIndexed
            val layout = textMeasurer.measure(label.text, if (label.isDay) dayStyle else clockStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x + labelPadding, (size.height * 0.55f - layout.size.height) / 2f),
            )
        }

        drawLine(
            color = colors.outlineVariant,
            start = Offset(0f, size.height - 0.5f),
            end = Offset(size.width, size.height - 0.5f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private data class HeaderLabel(val text: String, val isDay: Boolean)

private val MIN_LABEL_SPACING = 56.dp
private val MAX_LABEL_WIDTH = 120.dp
