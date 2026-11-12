package noel.chronova.core.domain.schedule

import noel.chronova.core.domain.TaskProblem
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency

/**
 * One change to a project's schedule. Each change carries the record as it was
 * when the change was worked out, so applying it can tell whether that record
 * has changed since (a manual edit, an earlier change or a sync) and refuse to
 * overwrite it.
 *
 * Every edit path (dragging a bar, a voice or typed command, undo) is expressed
 * as a list of these and applied by [ApplyScheduleChanges].
 */
sealed interface ScheduleChange {
    /** Replaces [before] with [after] (same task). */
    data class UpdateTask(val before: SchedulerTask, val after: SchedulerTask) : ScheduleChange {
        init {
            require(before.id == after.id && before.projectId == after.projectId) { "An update must keep the task's identity" }
        }
    }

    /** Adds [task]; its id must not belong to a live task. */
    data class CreateTask(val task: SchedulerTask) : ScheduleChange

    /** Deletes [task]. Links touching it must be removed in the same list. */
    data class DeleteTask(val task: SchedulerTask) : ScheduleChange

    data class AddLink(val link: TaskDependency) : ScheduleChange

    data class RemoveLink(val link: TaskDependency) : ScheduleChange
}

/** A project's live schedule at one moment. */
data class ScheduleState(
    val project: Project,
    val tasks: List<SchedulerTask>,
    val links: List<TaskDependency>,
)

/** Why a list of changes was not applied. Nothing is written when any problem is found. */
sealed interface ChangeProblem {
    /** The project no longer exists. */
    data object ProjectMissing : ChangeProblem

    /** These tasks or links changed after the changes were worked out. */
    data class Stale(val recordIds: Set<String>) : ChangeProblem

    /** The task would break a task rule (blank title, too short, outside the project...). */
    data class InvalidTask(val taskId: String, val problem: TaskProblem) : ChangeProblem

    /** A link would point at a task that doesn't exist, or a deleted task would keep links. */
    data class BrokenLink(val linkId: String) : ChangeProblem

    /** A new link would close a dependency cycle. */
    data class Cycle(val linkId: String) : ChangeProblem
}

sealed interface ApplyResult {
    /** Applied; applying [undo] reverses it. */
    data class Applied(val undo: List<ScheduleChange>) : ApplyResult

    data class Rejected(val problem: ChangeProblem) : ApplyResult
}
