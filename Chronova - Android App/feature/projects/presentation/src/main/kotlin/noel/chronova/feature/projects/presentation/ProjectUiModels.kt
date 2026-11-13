package noel.chronova.feature.projects.presentation

import java.time.LocalDate

/** One project on the list, as the screen shows it. */
data class ProjectRowUi(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val taskCount: Int,
    /** How many of its tasks are done. */
    val doneCount: Int = 0,
)

/** Why the entered project can't be saved, as the editor words it. */
sealed interface ProjectProblemUi {
    data object BlankName : ProjectProblemUi
    data object EndBeforeStart : ProjectProblemUi
    data class TooLong(val maxDays: Int) : ProjectProblemUi

    /** Shrinking the range would leave [count] tasks outside it. */
    data class ExcludesTasks(val count: Int) : ProjectProblemUi

    /** The project being copied was deleted meanwhile (e.g. by a sync). */
    data object SourceGone : ProjectProblemUi
}

/** A project a new one can start from: its tasks and links are copied, keeping its length. */
data class ProjectSourceUi(val id: String, val name: String, val startDate: LocalDate, val endDate: LocalDate) {
    /** Days after its first day (0 for a one-day project). */
    val extraDays: Long get() = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)
}
