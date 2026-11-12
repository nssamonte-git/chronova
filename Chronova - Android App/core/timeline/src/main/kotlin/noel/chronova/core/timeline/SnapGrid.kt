package noel.chronova.core.timeline

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The lines that edits snap to. [nearest] rounds to the closest line (ties toward
 * later times); [floor] and [ceil] give the line at or before / at or after a time
 * and are used to keep clamped edges on the grid.
 */
interface SnapGrid {
    fun nearest(timeMillis: Long): Long
    fun floor(timeMillis: Long): Long
    fun ceil(timeMillis: Long): Long

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        /**
         * Sub-day intervals use a fixed grid from the window start (local midnight).
         * Whole-day intervals follow local midnights, so a 23- or 25-hour DST day
         * doesn't shift every later grid line by an hour.
         */
        fun of(snapMinutes: Int, originMillis: Long, zone: ZoneId): SnapGrid =
            if (snapMinutes >= MINUTES_PER_DAY && snapMinutes % MINUTES_PER_DAY == 0) {
                DayGrid(snapMinutes / MINUTES_PER_DAY, originMillis, zone)
            } else {
                FixedGrid(snapMinutes * TimelineMath.MILLIS_PER_MINUTE, originMillis)
            }
    }
}

/** Every [intervalMillis] from [originMillis]. */
class FixedGrid(private val intervalMillis: Long, private val originMillis: Long) : SnapGrid {
    override fun nearest(timeMillis: Long) = TimeSnapper.snap(timeMillis, intervalMillis, originMillis)
    override fun floor(timeMillis: Long) = TimeSnapper.floor(timeMillis, intervalMillis, originMillis)
    override fun ceil(timeMillis: Long) = TimeSnapper.ceil(timeMillis, intervalMillis, originMillis)
}

/** Local midnights every [days] days, counted from the local date of [originMillis]. */
class DayGrid(private val days: Int, originMillis: Long, private val zone: ZoneId) : SnapGrid {
    init {
        require(days > 0) { "Day grid needs a positive step" }
    }

    private val originDate: LocalDate = Instant.ofEpochMilli(originMillis).atZone(zone).toLocalDate()

    private fun lineAt(index: Long): Long =
        originDate.plusDays(index * days).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Grid index of the last line at or before [timeMillis]. */
    private fun floorIndex(timeMillis: Long): Long {
        val date = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()
        // The local date's midnight is always at or before the time itself.
        return Math.floorDiv(ChronoUnit.DAYS.between(originDate, date), days.toLong())
    }

    override fun floor(timeMillis: Long): Long = lineAt(floorIndex(timeMillis))

    override fun ceil(timeMillis: Long): Long {
        val index = floorIndex(timeMillis)
        val below = lineAt(index)
        return if (below == timeMillis) below else lineAt(index + 1)
    }

    override fun nearest(timeMillis: Long): Long {
        val below = floor(timeMillis)
        if (below == timeMillis) return below
        val above = ceil(timeMillis)
        return if (timeMillis - below < above - timeMillis) below else above
    }
}
