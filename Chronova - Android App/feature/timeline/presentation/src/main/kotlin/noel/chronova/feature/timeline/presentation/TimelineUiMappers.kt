package noel.chronova.feature.timeline.presentation

import java.time.ZoneId
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.domain.TaskProblem
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency

// Domain to screen: the timeline, its gestures and the editor never see the domain's tasks or links.

internal fun SchedulerTask.toUi() = TaskUi(id = id, title = title, startMillis = startMillis, endMillis = endMillis, color = color)

/** [tasks] are the project's tasks, to tell whether the link is currently broken. */
internal fun TaskDependency.toUi(tasks: Map<String, SchedulerTask>): DependencyUi {
    val predecessor = tasks[predecessorId]
    val successor = tasks[successorId]
    val violated = predecessor != null && successor != null && isViolated(predecessor, successor)
    return DependencyUi(predecessorId = predecessorId, successorId = successorId, violated = violated)
}

internal fun TaskProblem.toUi(): TaskProblemUi = when (this) {
    TaskProblem.BlankTitle -> TaskProblemUi.BlankTitle
    TaskProblem.EndNotAfterStart -> TaskProblemUi.EndNotAfterStart
    is TaskProblem.TooShort -> TaskProblemUi.TooShort(minMinutes)
    TaskProblem.OutsideProject -> TaskProblemUi.OutsideProject
    is TaskProblem.NotesTooLong -> TaskProblemUi.NotesTooLong(maxLength)
    TaskProblem.BadProgress -> TaskProblemUi.BadProgress
}

/** The sample project as the timeline shows it, for previews and gesture tests. */
internal fun sampleTimeline(zone: ZoneId) = TimelineUiState(
    projectName = SampleTasks.project.name,
    tasks = SampleTasks.tasks(zone).map { it.toUi() },
    window = SampleTasks.project.window(zone),
    zone = zone,
)
