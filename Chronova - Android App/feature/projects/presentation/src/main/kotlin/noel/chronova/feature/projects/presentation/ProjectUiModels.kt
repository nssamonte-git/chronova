package noel.chronova.feature.projects.presentation

import java.time.LocalDate

/** One project on the list, as the screen shows it. */
data class ProjectRowUi(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val taskCount: Int,
)

/** Why the entered project can't be saved, as the editor words it. */
sealed interface ProjectProblemUi {
    data object BlankName : ProjectProblemUi
    data object EndBeforeStart : ProjectProblemUi
    data class TooLong(val maxDays: Int) : ProjectProblemUi

    /** Shrinking the range would leave [count] tasks outside it. */
    data class ExcludesTasks(val count: Int) : ProjectProblemUi
}
