package noel.chronova.app.core.timeline

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TimelineWindow

/** Fixed UTC reference day so tests never depend on the device time zone. */
internal object Fixtures {
    private val day: LocalDate = LocalDate.of(2026, 3, 2)
    val origin: Long = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val window = TimelineWindow(origin, origin + 3 * 24 * HOUR)

    fun at(hour: Int, minute: Int = 0): Long =
        day.atTime(LocalTime.of(hour, minute)).toInstant(ZoneOffset.UTC).toEpochMilli()

    fun task(startH: Int, startM: Int, endH: Int, endM: Int) =
        SchedulerTask("t", "Task", at(startH, startM), at(endH, endM))
}

internal const val MINUTE = 60_000L
internal const val HOUR = 60 * MINUTE
