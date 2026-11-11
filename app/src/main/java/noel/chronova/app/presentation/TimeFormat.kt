package noel.chronova.app.presentation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared label formatting for the header, task rows and drag feedback. */
object TimeFormat {
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val day: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    fun clock(millis: Long, zone: ZoneId): String = clock.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun day(millis: Long, zone: ZoneId): String = day.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun range(startMillis: Long, endMillis: Long, zone: ZoneId): String =
        "${clock(startMillis, zone)} – ${clock(endMillis, zone)}"
}
