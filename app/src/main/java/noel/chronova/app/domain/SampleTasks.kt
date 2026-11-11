package noel.chronova.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskColor
import noel.chronova.app.domain.model.TimelineWindow

/**
 * Sample schedule shown on launch. Uses a fixed reference date (no DST change in
 * common zones) so the app looks the same every day it is opened.
 */
object SampleTasks {
    val REFERENCE_DATE: LocalDate = LocalDate.of(2026, 3, 2)
    private const val WINDOW_DAYS = 3L

    /** Reference day 00:00 to +3 days, in [zone]; the start is the timeline origin. */
    fun window(zone: ZoneId): TimelineWindow = TimelineWindow(
        startMillis = millis(REFERENCE_DATE, LocalTime.MIDNIGHT, zone),
        endMillis = millis(REFERENCE_DATE.plusDays(WINDOW_DAYS), LocalTime.MIDNIGHT, zone),
    )

    fun tasks(zone: ZoneId): List<SchedulerTask> = listOf(
        task("ui-design", "UI Design", 9, 0, 11, 0, TaskColor.BLUE, zone),
        task("implementation", "Implementation", 11, 0, 14, 0, TaskColor.GREEN, zone),
        task("qa-testing", "QA Testing", 13, 30, 16, 0, TaskColor.PURPLE, zone),
        task("review", "Review", 16, 0, 17, 0, TaskColor.AMBER, zone),
    )

    private fun task(
        id: String,
        title: String,
        startH: Int,
        startM: Int,
        endH: Int,
        endM: Int,
        color: TaskColor,
        zone: ZoneId,
    ) = SchedulerTask(
        id = id,
        title = title,
        startMillis = millis(REFERENCE_DATE, LocalTime.of(startH, startM), zone),
        endMillis = millis(REFERENCE_DATE, LocalTime.of(endH, endM), zone),
        color = color,
    )

    private fun millis(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}
