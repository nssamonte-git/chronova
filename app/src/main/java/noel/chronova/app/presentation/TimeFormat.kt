package noel.chronova.app.presentation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared label formatting for the header, task rows and drag feedback. */
object TimeFormat {
    // Digits only, so it is locale-independent and safe to keep as a constant.
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun clock(millis: Long, zone: ZoneId): String = clock.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Day and month names follow the current locale, so the formatter is built per call. */
    fun day(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(zone))

    fun range(startMillis: Long, endMillis: Long, zone: ZoneId): String =
        "${clock(startMillis, zone)} – ${clock(endMillis, zone)}"
}
