package noel.chronova.core.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared label formatting for the header, task rows, drag feedback and project list. */
object TimeFormat {
    // Digits only, so it is locale-independent and safe to keep as a constant.
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun clock(millis: Long, zone: ZoneId): String = clock.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** A time of day, e.g. "13:30". */
    fun clock(time: LocalTime): String = clock.format(time)

    /** Day and month names follow the current locale, so the formatter is built per call. */
    fun day(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(zone))

    /** e.g. "Tue 3". */
    fun shortDay(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(zone))

    /** e.g. "9 Mar". */
    fun dayMonth(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(zone))

    /** e.g. "Mar 2026". */
    fun month(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(zone))

    fun range(startMillis: Long, endMillis: Long, zone: ZoneId): String =
        "${clock(startMillis, zone)} – ${clock(endMillis, zone)}"

    /**
     * A task's span as its row shows it: clock times ("09:00 – 17:00"), or, for whole days
     * (midnight to a later midnight, as tasks read from a chart are), the days it covers, short
     * enough for the name column: "13–19 Nov", "30 Nov – 6 Dec", or "13 Nov" for one day.
     */
    fun span(startMillis: Long, endMillis: Long, zone: ZoneId): String {
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        val end = Instant.ofEpochMilli(endMillis).atZone(zone)
        return if (isWholeDays(startMillis, endMillis, zone)) shortDays(start.toLocalDate(), end.toLocalDate().minusDays(1)) else range(startMillis, endMillis, zone)
    }

    /** Midnight to a later midnight in [zone]: a task on whole days, as tasks read from a chart are. */
    fun isWholeDays(startMillis: Long, endMillis: Long, zone: ZoneId): Boolean =
        endMillis > startMillis &&
            Instant.ofEpochMilli(startMillis).atZone(zone).toLocalTime() == LocalTime.MIDNIGHT &&
            Instant.ofEpochMilli(endMillis).atZone(zone).toLocalTime() == LocalTime.MIDNIGHT

    /**
     * A task's span with its days, as the assistant's preview shows it: "Thu 12 Nov 09:00 – 11:00",
     * "Thu 12 Nov 09:00 – Fri 13 Nov 17:00" across days, or "Fri 13 Nov 17:00" for a moment (a
     * milestone). Whole days show the days alone, the last one inclusive ("Thu 26 Nov",
     * "Mon 16 Nov – Wed 18 Nov"): as clock times a one-day task read "00:00 – 00:00".
     */
    fun daySpan(startMillis: Long, endMillis: Long, zone: ZoneId): String {
        val startDay = day(startMillis, zone)
        if (startMillis == endMillis) return "$startDay ${clock(startMillis, zone)}"
        // The day the span ends on: one that ends at midnight ends on the day before.
        val lastDay = day(endMillis - 1, zone)
        return when {
            isWholeDays(startMillis, endMillis, zone) -> if (startDay == lastDay) startDay else "$startDay – $lastDay"
            startDay == lastDay -> "$startDay ${range(startMillis, endMillis, zone)}"
            else -> "$startDay ${clock(startMillis, zone)} – ${day(endMillis, zone)} ${clock(endMillis, zone)}"
        }
    }

    /** Inclusive days without the year: "13–19 Nov", "30 Nov – 6 Dec", "13 Nov". */
    private fun shortDays(first: LocalDate, last: LocalDate): String {
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return when {
            first == last -> dayMonth.format(first)
            first.month == last.month && first.year == last.year -> "${first.dayOfMonth}–${dayMonth.format(last)}"
            else -> "${dayMonth.format(first)} – ${dayMonth.format(last)}"
        }
    }

    /** Inclusive date range, e.g. "2 Mar – 4 Mar 2026"; the year is shown once when shared. */
    fun dateRange(start: LocalDate, end: LocalDate): String {
        val locale = Locale.getDefault()
        val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        if (start == end) return full.format(start)
        val first = if (start.year == end.year) DateTimeFormatter.ofPattern("d MMM", locale) else full
        return "${first.format(start)} – ${full.format(end)}"
    }
}
