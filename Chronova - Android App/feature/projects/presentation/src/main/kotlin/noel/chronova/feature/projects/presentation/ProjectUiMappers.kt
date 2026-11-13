package noel.chronova.feature.projects.presentation

import noel.chronova.core.domain.ProjectProblem
import java.time.ZoneId
import noel.chronova.core.model.ProjectSummary
import noel.chronova.core.model.TaskOutline

// Domain to screen: the list and the editor never see the domain types.

/**
 * [outlines] are this project's tasks in row order; the first [MAX_CARD_BARS] become the
 * card's small chart, placed within the project's days in [zone].
 */
internal fun ProjectSummary.toUi(outlines: List<TaskOutline> = emptyList(), zone: ZoneId = ZoneId.systemDefault()): ProjectRowUi {
    val window = project.window(zone)
    val length = (window.endMillis - window.startMillis).toFloat()
    fun share(millis: Long) = ((millis - window.startMillis) / length).coerceIn(0f, 1f)
    return ProjectRowUi(
        id = project.id,
        name = project.name,
        startDate = project.startDate,
        endDate = project.endDate,
        taskCount = taskCount,
        doneCount = doneCount,
        bars = outlines.take(MAX_CARD_BARS).map { ProjectBarUi(share(it.startMillis), share(it.endMillis), it.color, it.isMilestone) },
    )
}

/** How many tasks a card's small chart shows: enough to see the project's shape, short enough for a list. */
internal const val MAX_CARD_BARS = 5

internal fun ProjectProblem.toUi(): ProjectProblemUi = when (this) {
    ProjectProblem.BlankName -> ProjectProblemUi.BlankName
    ProjectProblem.EndBeforeStart -> ProjectProblemUi.EndBeforeStart
    is ProjectProblem.TooLong -> ProjectProblemUi.TooLong(maxDays)
    is ProjectProblem.ExcludesTasks -> ProjectProblemUi.ExcludesTasks(count)
}

internal fun ProjectRowUi.toSource() = ProjectSourceUi(id, name, startDate, endDate)
