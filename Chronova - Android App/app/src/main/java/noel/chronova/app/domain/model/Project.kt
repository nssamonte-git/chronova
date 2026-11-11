package noel.chronova.app.domain.model

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

/** A project plus what the project list shows about it. */
data class ProjectSummary(
    val project: Project,
    val taskCount: Int,
)
