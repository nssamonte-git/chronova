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
        val wholeDays = endMillis > startMillis && start.toLocalTime() == LocalTime.MIDNIGHT && end.toLocalTime() == LocalTime.MIDNIGHT
        return if (wholeDays) shortDays(start.toLocalDate(), end.toLocalDate().minusDays(1)) else range(startMillis, endMillis, zone)
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
