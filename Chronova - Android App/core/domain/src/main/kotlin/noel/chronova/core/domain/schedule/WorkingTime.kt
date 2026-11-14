package noel.chronova.core.domain.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.EnumSet

/**
 * The hours that count as work: 09:00 to 17:00, Monday to Friday, in [zone]. Fixed for every
 * project for now (user choice, 2026-11-14): this is the one place to change when working hours
 * become a setting.
 */
class WorkingTime(
    private val zone: ZoneId,
    private val from: LocalTime = LocalTime.of(9, 0),
    private val until: LocalTime = LocalTime.of(17, 0),
    private val days: Set<DayOfWeek> = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
) {
    /** Measures working time between [fromMillis] and [toMillis] (both inside what it can measure). */
    fun ruler(fromMillis: Long, toMillis: Long): Ruler {
        val starts = ArrayList<Long>()
        val ends = ArrayList<Long>()
        var day: LocalDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val last: LocalDate = Instant.ofEpochMilli(maxOf(fromMillis, toMillis)).atZone(zone).toLocalDate()
        while (!day.isAfter(last)) {
            if (day.dayOfWeek in days) {
                val start = day.atTime(from).atZone(zone).toInstant().toEpochMilli()
                val end = day.atTime(until).atZone(zone).toInstant().toEpochMilli()
                if (end > start) {
                    starts += start
                    ends += end
                }
            }
            day = day.plusDays(1)
        }
        return Ruler(starts.toLongArray(), ends.toLongArray())
    }

    /** The working stretches of a span of days, in order, with the working time before each. */
    class Ruler internal constructor(private val starts: LongArray, private val ends: LongArray) {
        private val before = LongArray(starts.size).also { sums ->
            for (i in 1 until starts.size) sums[i] = sums[i - 1] + (ends[i - 1] - starts[i - 1])
        }

        /** The working time from the ruler's first day up to [millis]. */
        fun workedBefore(millis: Long): Long {
            val i = stretchAt(millis)
            return if (i < 0) 0L else before[i] + (minOf(millis, ends[i]) - starts[i])
        }

        /** Whether [millis] falls in working hours, their first and last moments included. */
        fun isWorking(millis: Long): Boolean {
            val i = stretchAt(millis)
            return i >= 0 && millis <= ends[i]
        }

        /** The last stretch that starts at or before [millis]; -1 before the first. */
        private fun stretchAt(millis: Long): Int {
            var low = 0
            var high = starts.size - 1
            var found = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (starts[mid] <= millis) {
                    found = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return found
        }
    }
}
