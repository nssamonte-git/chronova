package noel.chronova.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskColor

/**
 * The sample project created on first launch. Uses a fixed reference date (no DST
 * change in common zones) so it looks the same every day it is opened.
 */
object SampleTasks {
    const val PROJECT_ID = "sample-project"
    val REFERENCE_DATE: LocalDate = LocalDate.of(2026, 3, 2)
    private const val PROJECT_DAYS = 3L

    val project: Project = Project(
        id = PROJECT_ID,
        name = "Sample project",
        startDate = REFERENCE_DATE,
        endDate = REFERENCE_DATE.plusDays(PROJECT_DAYS - 1),
    )

    fun tasks(zone: ZoneId): List<SchedulerTask> = listOf(
        task("ui-design", "UI Design", 9, 0, 11, 0, TaskColor.BLUE, 0, zone),
        task("implementation", "Implementation", 11, 0, 14, 0, TaskColor.GREEN, 1, zone),
        task("qa-testing", "QA Testing", 13, 30, 16, 0, TaskColor.PURPLE, 2, zone),
        task("review", "Review", 16, 0, 17, 0, TaskColor.AMBER, 3, zone),
    )

    private fun task(
        id: String,
        title: String,
        startH: Int,
        startM: Int,
        endH: Int,
        endM: Int,
        color: TaskColor,
        sortOrder: Int,
        zone: ZoneId,
    ) = SchedulerTask(
        id = id,
        projectId = PROJECT_ID,
        title = title,
        startMillis = millis(REFERENCE_DATE, LocalTime.of(startH, startM), zone),
        endMillis = millis(REFERENCE_DATE, LocalTime.of(endH, endM), zone),
        color = color,
        sortOrder = sortOrder,
    )

    private fun millis(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}
