package noel.chronova.app.domain.timeline

import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import noel.chronova.app.domain.DependencyRules
import noel.chronova.app.domain.TaskProblem
import noel.chronova.app.domain.TaskRules
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskColor
import noel.chronova.app.domain.model.TaskDependency
import noel.chronova.app.domain.model.TimelineConfig
import noel.chronova.app.domain.model.TimelineWindow
import noel.chronova.app.domain.repository.ProjectRepository
import noel.chronova.app.domain.schedule.ApplyResult
import noel.chronova.app.domain.schedule.ApplyScheduleChanges
import noel.chronova.app.domain.schedule.ScheduleChange

/** One project's schedule as the timeline shows it; tasks in row order. */
data class ProjectSchedule(
    val project: Project,
    val tasks: List<SchedulerTask>,
    val dependencies: List<TaskDependency>,
)

/** The project with its live tasks and links; emits null once the project is gone. */
class ObserveProjectSchedule(private val repository: ProjectRepository) {
    operator fun invoke(projectId: String): Flow<ProjectSchedule?> = combine(
        repository.observeProject(projectId),
        repository.observeTasks(projectId),
        repository.observeDependencies(projectId),
    ) { project, tasks, dependencies -> project?.let { ProjectSchedule(it, tasks, dependencies) } }
}

/**
 * Saves a dragged or resized task through the shared apply path, computed from
 * [original] (the task as the drag started); refused if it changed meanwhile.
 */
class CommitTaskEdit(private val applyChanges: ApplyScheduleChanges) {
    suspend operator fun invoke(projectId: String, original: SchedulerTask, updated: SchedulerTask): ApplyResult =
        applyChanges(projectId, listOf(ScheduleChange.UpdateTask(original, updated)))
}

/** The first problem with a task as entered in the editor, or null when it can be saved. */
class ValidateTask {
    operator fun invoke(title: String, startMillis: Long, endMillis: Long, window: TimelineWindow): TaskProblem? =
        TaskRules.validate(title, startMillis, endMillis, window)
}

/** Tasks that can't become [taskId]'s predecessors (itself, or a link that would close a cycle). */
class ForbiddenPredecessors {
    operator fun invoke(taskId: String, dependencies: List<TaskDependency>): Set<String> =
        DependencyRules.forbiddenPredecessors(taskId, dependencies)
}

/**
 * Creates a task ([taskId] null) or saves the editor's changes to [existing] (the task
 * as the timeline has it; null if it vanished meanwhile, then only its links are set),
 * then makes [predecessorIds] its predecessors, re-checked against the schedule as it
 * is now (only live tasks, never a cycle). Returns the task's id.
 */
class SaveTask(private val repository: ProjectRepository) {
    suspend operator fun invoke(
        projectId: String,
        taskId: String?,
        existing: SchedulerTask?,
        title: String,
        startMillis: Long,
        endMillis: Long,
        color: TaskColor,
        predecessorIds: Set<String>,
    ): String {
        val id = if (taskId == null) {
            repository.createTask(projectId, title, startMillis, endMillis, color)
        } else {
            if (existing != null) {
                repository.updateTask(existing.copy(title = title, startMillis = startMillis, endMillis = endMillis, color = color))
            }
            taskId
        }
        val now = repository.schedule(projectId)
        val validIds = now?.tasks?.map { it.id }?.toSet().orEmpty()
        val forbidden = DependencyRules.forbiddenPredecessors(id, now?.links.orEmpty())
        repository.setPredecessors(projectId, id, predecessorIds.filter { it in validIds && it !in forbidden }.toSet())
        return id
    }
}

/** Deletes [task] with its links in one change set; the result carries the undo. */
class DeleteTask(private val applyChanges: ApplyScheduleChanges) {
    suspend operator fun invoke(projectId: String, task: SchedulerTask, dependencies: List<TaskDependency>): ApplyResult {
        val links = dependencies.filter { it.predecessorId == task.id || it.successorId == task.id }
        return applyChanges(projectId, links.map { ScheduleChange.RemoveLink(it) } + ScheduleChange.DeleteTask(task))
    }
}

/** Applies an undo change set, refused if those tasks were edited again since. */
class UndoScheduleChanges(private val applyChanges: ApplyScheduleChanges) {
    suspend operator fun invoke(projectId: String, changes: List<ScheduleChange>): ApplyResult = applyChanges(projectId, changes)
}

/** How this project's timeline was last viewed on this device, or the defaults. */
class LoadViewSettings(private val repository: ProjectRepository) {
    suspend operator fun invoke(projectId: String): TimelineConfig = repository.viewSettings(projectId) ?: TimelineConfig()
}

class SaveViewSettings(private val repository: ProjectRepository) {
    suspend operator fun invoke(projectId: String, config: TimelineConfig) = repository.saveViewSettings(projectId, config)
}

/** Everything the timeline screen does. */
class TimelineUseCases(
    val observeSchedule: ObserveProjectSchedule,
    val commitEdit: CommitTaskEdit,
    val validateTask: ValidateTask,
    val forbiddenPredecessors: ForbiddenPredecessors,
    val saveTask: SaveTask,
    val deleteTask: DeleteTask,
    val undo: UndoScheduleChanges,
    val loadViewSettings: LoadViewSettings,
    val saveViewSettings: SaveViewSettings,
)
