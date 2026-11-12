package noel.chronova.core.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask

/** Why a project can't be saved as entered. */
sealed interface ProjectProblem {
    data object BlankName : ProjectProblem
    data object EndBeforeStart : ProjectProblem
    data class TooLong(val maxDays: Int) : ProjectProblem
    /** Shrinking the range would leave [count] tasks outside it. */
    data class ExcludesTasks(val count: Int) : ProjectProblem
}

object ProjectRules {
    /** Longest project, in days. Keeps tick lists and float pixel positions in a sane range. */
    const val MAX_DAYS = 366

    /** The first problem with the entered values, or null when they can be saved. */
    fun validate(
        name: String,
        startDate: LocalDate,
        endDate: LocalDate,
        existingTasks: List<SchedulerTask>,
        zone: ZoneId,
    ): ProjectProblem? {
        if (name.isBlank()) return ProjectProblem.BlankName
        if (endDate.isBefore(startDate)) return ProjectProblem.EndBeforeStart
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_DAYS) return ProjectProblem.TooLong(MAX_DAYS)
        val window = Project("validate", name, startDate, endDate).window(zone)
        val outside = existingTasks.count { it.startMillis < window.startMillis || it.endMillis > window.endMillis }
        return if (outside > 0) ProjectProblem.ExcludesTasks(outside) else null
    }
}
