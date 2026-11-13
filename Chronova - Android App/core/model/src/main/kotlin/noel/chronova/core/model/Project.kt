package noel.chronova.core.model

import java.time.LocalDate
import java.time.ZoneId

/**
 * A user's project. The schedule spans whole calendar days, [startDate] to
 * [endDate] inclusive, interpreted in the viewer's time zone.
 */
data class Project(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(name.isNotBlank()) { "Project name must not be blank" }
        require(!endDate.isBefore(startDate)) { "Project '$id' must not end before it starts" }
    }

    /** startDate 00:00 to the midnight after endDate, in [zone]. */
    fun window(zone: ZoneId): TimelineWindow = TimelineWindow(
        startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}

/**
 * Where a task sits and its colour, without its words: all a small chart of a project needs
 * (the project list draws one on each card).
 */
data class TaskOutline(
    val projectId: String,
    val startMillis: Long,
    val endMillis: Long,
    val color: TaskColor,
    val isMilestone: Boolean = false,
)

/** A project plus what the project list shows about it: its tasks and how many are done. */
data class ProjectSummary(
    val project: Project,
    val taskCount: Int,
    val doneCount: Int = 0,
)
