package noel.chronova.app.core.timeline

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong
import noel.chronova.app.domain.model.TimeScale
import noel.chronova.app.domain.model.TimelineWindow

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
     * Calendar grid ticks across [window], in [zone]. Major ticks start a scale unit
     * and are labelled; minor ticks subdivide it. Shared by the header and the grid
     * so both are always aligned.
     *
     * - Hours: major every local hour, minor at half past.
     * - Days: major at local midnight, minor at 06:00, 12:00 and 18:00.
     * - Weeks: major at Monday midnight, minor at every other midnight.
     * - Months: major on the 1st, minor on Mondays.
     *
     * Positions follow the real calendar, so months have their true lengths and a
     * DST day is 23 or 25 hours wide.
     */
    fun ticks(window: TimelineWindow, zone: ZoneId, scale: TimeScale): List<Tick> {
        val ticks = ArrayList<Tick>()
        var majorIndex = 0
        fun add(timeMillis: Long, isMajor: Boolean) {
            if (timeMillis < window.startMillis || timeMillis > window.endMillis) return
            ticks += Tick(timeMillis - window.startMillis, isMajor, if (isMajor) majorIndex++ else -1)
        }
        fun midnight(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

        if (scale == TimeScale.HOURS) {
            val step = 30 * MILLIS_PER_MINUTE
            var t = window.startMillis
            while (t <= window.endMillis) {
                add(t, Instant.ofEpochMilli(t).atZone(zone).minute == 0)
                t += step
            }
            return ticks
        }

        val firstDay = Instant.ofEpochMilli(window.startMillis).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(window.endMillis).atZone(zone).toLocalDate()
        var day = firstDay
        while (!day.isAfter(lastDay)) {
            when (scale) {
                TimeScale.DAYS -> {
                    add(midnight(day), isMajor = true)
                    for (hour in intArrayOf(6, 12, 18)) {
                        add(day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), isMajor = false)
                    }
                }
                TimeScale.WEEKS -> add(midnight(day), isMajor = day.dayOfWeek == DayOfWeek.MONDAY)
                TimeScale.MONTHS -> when {
                    day.dayOfMonth == 1 -> add(midnight(day), isMajor = true)
                    day.dayOfWeek == DayOfWeek.MONDAY -> add(midnight(day), isMajor = false)
                }
                TimeScale.HOURS -> Unit // handled above
            }
            day = day.plusDays(1)
        }
        return ticks
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

/**
 * A grid line at [offsetMillis] from the timeline origin. [majorIndex] counts the
 * major ticks (0, 1, 2…) so labels can be thinned evenly; it is -1 for minor ticks.
 */
data class Tick(val offsetMillis: Long, val isMajor: Boolean, val majorIndex: Int = -1)
