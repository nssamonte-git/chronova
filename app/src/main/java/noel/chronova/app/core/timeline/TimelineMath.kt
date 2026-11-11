package noel.chronova.app.core.timeline

import kotlin.math.roundToLong
import noel.chronova.app.domain.model.TimeScale

/**
 * Pure conversions between time and horizontal pixels.
 *
 * Every x is measured from one origin (the timeline window start):
 *   x     = (time - origin) in minutes × pxPerMinute
 *   width = duration in minutes × pxPerMinute
 * Times are never stored as pixels; pixels are derived at render time.
 */
object TimelineMath {
    const val MILLIS_PER_MINUTE = 60_000L

    /** Pixels per minute for a given zoom (px width of one unit) and scale. */
    fun pxPerMinute(unitWidthPx: Float, scale: TimeScale): Float = unitWidthPx / scale.minutes

    /** X position of [timeMillis]; negative when before the origin. */
    fun timeToX(timeMillis: Long, originMillis: Long, pxPerMinute: Float): Float =
        ((timeMillis - originMillis).toDouble() / MILLIS_PER_MINUTE * pxPerMinute).toFloat()

    /** Time at horizontal position [x], rounded to the nearest whole minute. */
    fun xToTime(x: Float, originMillis: Long, pxPerMinute: Float): Long =
        originMillis + pxToMillis(x, pxPerMinute)

    /** Width of a span of [durationMillis]. */
    fun durationToWidth(durationMillis: Long, pxPerMinute: Float): Float =
        (durationMillis.toDouble() / MILLIS_PER_MINUTE * pxPerMinute).toFloat()

    /**
     * Converts a drag distance into a (signed) time delta, rounded to whole minutes.
     *
     * Pixels are Floats: at x ≈ 1300 px a Float resolves ~1e-4 px, which is several
     * milliseconds at typical zoom. Rounding to the minute (the timeline's real
     * resolution) makes px → time → px round-trips exact instead of off by 1 ms.
     */
    fun pxToMillis(px: Float, pxPerMinute: Float): Long =
        (px.toDouble() / pxPerMinute).roundToLong() * MILLIS_PER_MINUTE

    /**
     * Grid ticks from the origin across [lengthMillis]: a major tick per scale unit
     * (labelled) and minor ticks between them (30 min for hours, 6 h for days).
     * Shared by the header and the grid so both are always aligned.
     */
    fun ticks(lengthMillis: Long, scale: TimeScale): List<Tick> {
        val majorMinutes = scale.minutes
        val minorMinutes = when (scale) {
            TimeScale.HOURS -> 30L
            TimeScale.DAYS -> 6 * 60L
        }
        val lengthMinutes = lengthMillis / MILLIS_PER_MINUTE
        return (0..lengthMinutes step minorMinutes).map { minute ->
            Tick(offsetMillis = minute * MILLIS_PER_MINUTE, isMajor = minute % majorMinutes == 0L)
        }
    }

    /**
     * Indices of the [ticks] (sorted by offset) that lie within
     * [fromOffsetMillis]..[toOffsetMillis], found by binary search so drawing a long
     * timeline only visits the visible ticks. Empty when none fall inside.
     */
    fun visibleTicks(ticks: List<Tick>, fromOffsetMillis: Long, toOffsetMillis: Long): IntRange {
        val first = lowerBound(ticks, fromOffsetMillis)
        val endExclusive = lowerBound(ticks, toOffsetMillis + 1)
        return first until endExclusive
    }

    /** First index whose offset is >= [offsetMillis]. */
    private fun lowerBound(ticks: List<Tick>, offsetMillis: Long): Int {
        var low = 0
        var high = ticks.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (ticks[mid].offsetMillis < offsetMillis) low = mid + 1 else high = mid
        }
        return low
    }
}

/** A grid line at [offsetMillis] from the timeline origin. */
data class Tick(val offsetMillis: Long, val isMajor: Boolean)
