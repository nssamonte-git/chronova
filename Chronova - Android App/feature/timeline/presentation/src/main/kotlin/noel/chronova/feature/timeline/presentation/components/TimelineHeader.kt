package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.Canvas
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
import noel.chronova.core.timeline.Tick
import noel.chronova.core.timeline.TimelineMath
import noel.chronova.core.model.TimeScale
import noel.chronova.core.ui.TimeFormat

/**
 * Time-axis header. It reads the shared [viewport] scroll inside the draw lambda,
 * so scrolling only re-draws this canvas (no recomposition) and the labels can
 * never drift from the grid below. Only visible ticks are visited.
 *
 * Swipes on the header feed the same viewport through `scrollable`, so there is
 * still exactly one scroll position. `reverseDirection = true` makes a drag to the
 * left reveal later times.
 */
@Composable
fun TimelineHeader(
    originMillis: Long,
    zone: ZoneId,
    scale: TimeScale,
    ticks: List<Tick>,
    pxPerMinute: Float,
    viewport: TimelineViewportState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val clockStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant)
    val dayStyle = clockStyle.copy(color = colors.onSurface, fontWeight = FontWeight.SemiBold)
    val minorStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant.copy(alpha = 0.8f))

    // Labels depend only on time, never on zoom. They are formatted lazily, the
    // first time a tick is drawn, so a year at Hours scale doesn't format ~17k up front.
    val labels = remember(ticks, originMillis, zone, scale) { arrayOfNulls<HeaderLabel>(ticks.size) }
    fun labelFor(index: Int): HeaderLabel = labels[index] ?: headerLabel(
        millis = originMillis + ticks[index].offsetMillis,
        isMajor = ticks[index].isMajor,
        zone = zone,
        scale = scale,
    ).also { labels[index] = it }

    // Major labels thin out evenly (every n-th major tick) when units get narrow.
    // Minor ticks get labels only when they are far enough apart, e.g. 06:00/12:00/18:00
    // in Days scale, so a zoomed-in view never shows a header without text.
    val density = LocalDensity.current
    val minSpacingPx = with(density) { MIN_LABEL_SPACING.toPx() }
    val unitWidthPx = pxPerMinute * scale.minutes
    val labelStride = max(1, ceil(minSpacingPx / unitWidthPx).toInt())
    val showMinorLabels = pxPerMinute * scale.minorMinutes >= minSpacingPx

    Canvas(
        modifier
            .clipToBounds()
            .scrollable(viewport.scrollableState, Orientation.Horizontal, reverseDirection = true),
    ) {
        val scrollX = viewport.scrollX // draw-phase read: no recomposition on scroll
        val labelPadding = 6.dp.toPx()
        val cullMargin = MAX_LABEL_WIDTH.toPx()
        // Labels start right of their tick, so ticks up to one label width left of the
        // viewport are still visited.
        val visible = TimelineMath.visibleTicks(
            ticks,
            fromOffsetMillis = TimelineMath.pxToMillis(scrollX - cullMargin, pxPerMinute),
            toOffsetMillis = TimelineMath.pxToMillis(scrollX + size.width, pxPerMinute),
        )

        val labelGap = 8.dp.toPx()
        // The labels drawn so far, as [left, right) spans: major labels go first and win, and a
        // minor label that would run into any of them is left out (in Months, "30 Nov" a day before
        // "Dec 2026"; a long month name reaching past the next Monday).
        val taken = ArrayList<ClosedFloatingPointRange<Float>>()
        fun fits(left: Float, right: Float) = taken.none { left < it.endInclusive + labelGap && right + labelGap > it.start }

        for (pass in 0..1) {
            for (index in visible) {
                val tick = ticks[index]
                val x = TimelineMath.durationToWidth(tick.offsetMillis, pxPerMinute) - scrollX
                if (pass == 0) {
                    val lineTop = if (tick.isMajor) size.height * 0.55f else size.height * 0.75f
                    drawLine(
                        color = if (tick.isMajor) colors.outline else colors.outlineVariant,
                        start = Offset(x, lineTop),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                // Majors' labels in the first pass, minors' in the second.
                if (tick.isMajor != (pass == 0)) continue

                val style = when {
                    tick.isMajor -> {
                        if (tick.majorIndex % labelStride != 0) continue
                        if (labelFor(index).emphasized) dayStyle else clockStyle
                    }
                    showMinorLabels -> minorStyle
                    else -> continue
                }
                val layout = textMeasurer.measure(labelFor(index).text, style)
                val left = x + labelPadding
                val right = left + layout.size.width
                if (!tick.isMajor && !fits(left, right)) continue
                taken += left..right
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(left, (size.height * 0.55f - layout.size.height) / 2f),
                )
            }
        }

        drawLine(
            color = colors.outlineVariant,
            start = Offset(0f, size.height - 0.5f),
            end = Offset(size.width, size.height - 0.5f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private data class HeaderLabel(val text: String, val emphasized: Boolean)

/**
 * Hours: clock times, with the date (emphasized) at midnight. Days: the date, then
 * clock times for the 6-hour minors. Weeks: the date on Mondays, short day names
 * between. Months: month and year, then the dates of Mondays.
 */
private fun headerLabel(millis: Long, isMajor: Boolean, zone: ZoneId, scale: TimeScale): HeaderLabel = when (scale) {
    TimeScale.HOURS -> {
        val isMidnight = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().toSecondOfDay() == 0
        if (isMajor && isMidnight) HeaderLabel(TimeFormat.day(millis, zone), true) else HeaderLabel(TimeFormat.clock(millis, zone), false)
    }
    TimeScale.DAYS ->
        if (isMajor) HeaderLabel(TimeFormat.day(millis, zone), true) else HeaderLabel(TimeFormat.clock(millis, zone), false)
    TimeScale.WEEKS ->
        if (isMajor) HeaderLabel(TimeFormat.day(millis, zone), true) else HeaderLabel(TimeFormat.shortDay(millis, zone), false)
    TimeScale.MONTHS ->
        if (isMajor) HeaderLabel(TimeFormat.month(millis, zone), true) else HeaderLabel(TimeFormat.dayMonth(millis, zone), false)
}

private val MIN_LABEL_SPACING = 56.dp
private val MAX_LABEL_WIDTH = 120.dp
