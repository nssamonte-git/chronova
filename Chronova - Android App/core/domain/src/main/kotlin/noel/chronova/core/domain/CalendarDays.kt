package noel.chronova.core.domain

import java.time.Instant
import java.time.ZoneId

/** Moving times by whole calendar days, so a 23- or 25-hour DST day keeps local times. */
object CalendarDays {
    /** [millis] moved [days] calendar days (negative for earlier), keeping its local time of day in [zone]. */
    fun shift(millis: Long, days: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).plusDays(days).toInstant().toEpochMilli()
}
