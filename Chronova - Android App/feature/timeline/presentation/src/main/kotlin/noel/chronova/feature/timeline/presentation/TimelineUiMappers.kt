package noel.chronova.feature.timeline.presentation

import java.time.ZoneId
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.domain.TaskProblem
import noel.chronova.core.domain.schedule.CriticalPath
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency

// Domain to screen: the timeline, its gestures and the editor never see the domain's tasks or links.

internal fun SchedulerTask.toUi(slackMillis: Long? = null) = TaskUi(
    id = id, title = title, startMillis = startMillis, endMillis = endMillis, color = color,
    progress = progress, notes = notes, isMilestone = isMilestone, slackMillis = slackMillis,
)

/**
 * [tasks] are the project's tasks, to tell whether the link is currently broken;
 * [critical] tells whether a task is on the critical path.
 */
internal fun TaskDependency.toUi(tasks: Map<String, SchedulerTask>, critical: (String) -> Boolean = { false }): DependencyUi {
    val predecessor = tasks[predecessorId]
    val successor = tasks[successorId]
    val violated = predecessor != null && successor != null && isViolated(predecessor, successor)
    return DependencyUi(
        predecessorId = predecessorId,
        successorId = successorId,
        violated = violated,
        critical = critical(predecessorId) && critical(successorId),
    )
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
internal fun sampleTimeline(zone: ZoneId): TimelineUiState {
    val tasks = SampleTasks.tasks(zone)
    val path = CriticalPath.of(tasks, emptyList())
    return TimelineUiState(
        projectName = SampleTasks.project.name,
        tasks = tasks.map { it.toUi(path.slackMillis[it.id]) },
        window = SampleTasks.project.window(zone),
        zone = zone,
        finishMillis = path.finishMillis,
    )
}
