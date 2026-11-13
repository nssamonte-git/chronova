package noel.chronova.feature.projects.presentation

import java.time.LocalDate
import noel.chronova.core.model.TaskColor

/** One project on the list, as the screen shows it. */
data class ProjectRowUi(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val taskCount: Int,
    /** How many of its tasks are done. */
    val doneCount: Int = 0,
    /**
     * Its tasks, for the card's small chart, as rows of bars from top to bottom; empty when it
     * has none. A project with a few tasks has one row per task, in row order; a larger one has
     * every task packed into a few rows, so the chart shows the whole project at the same height.
     */
    val lanes: List<List<ProjectBarUi>> = emptyList(),
)

/**
 * One task on a project card's small chart: where it starts and ends, as shares of the
 * project's length (0 is its first midnight, 1 the midnight after its last day). A very short
 * task is given a small minimum length, so it can be seen.
 */
data class ProjectBarUi(
    val start: Float,
    val end: Float,
    val color: TaskColor,
    /** A moment rather than a span: drawn as a dot at [start] ([end] is only the room it takes in its row). */
    val milestone: Boolean = false,
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
