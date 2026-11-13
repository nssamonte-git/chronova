package noel.chronova.feature.projects.presentation

import noel.chronova.core.domain.ProjectProblem
import noel.chronova.core.model.ProjectSummary

// Domain to screen: the list and the editor never see the domain types.

internal fun ProjectSummary.toUi() = ProjectRowUi(
    id = project.id,
    name = project.name,
    startDate = project.startDate,
    endDate = project.endDate,
    taskCount = taskCount,
    doneCount = doneCount,
)

internal fun ProjectProblem.toUi(): ProjectProblemUi = when (this) {
    ProjectProblem.BlankName -> ProjectProblemUi.BlankName
    ProjectProblem.EndBeforeStart -> ProjectProblemUi.EndBeforeStart
    is ProjectProblem.TooLong -> ProjectProblemUi.TooLong(maxDays)
    is ProjectProblem.ExcludesTasks -> ProjectProblemUi.ExcludesTasks(count)
}
