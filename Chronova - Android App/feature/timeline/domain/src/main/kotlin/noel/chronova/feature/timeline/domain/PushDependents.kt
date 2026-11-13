package noel.chronova.feature.timeline.domain

import java.time.ZoneId
import noel.chronova.core.domain.repository.ProjectRepository
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.domain.schedule.DependentsRescheduler
import noel.chronova.core.domain.schedule.ScheduleChange

/** What pushing the tasks that wait for one task would do, worked out without writing. */
sealed interface DependentsPlan {
    /** Nothing waits for it, or everything that does still starts after it ends. */
    data object NothingToPush : DependentsPlan

    /** These tasks start before the task ends and can be pushed later. */
    data class CanPush(val taskTitle: String, val count: Int) : DependentsPlan

    /** Pushing them would run these tasks past the project's end ([projectEndMillis]). */
    data class DoesNotFit(val taskTitle: String, val titles: List<String>, val projectEndMillis: Long) : DependentsPlan
}

sealed interface PushResult {
    data class Pushed(val count: Int, val undo: List<ScheduleChange>) : PushResult

    /** Nothing needed pushing any more. */
    data object NothingToPush : PushResult

    data class DoesNotFit(val titles: List<String>, val projectEndMillis: Long) : PushResult

    /** The schedule changed meanwhile and the push was refused. */
    data object Refused : PushResult
}

/**
 * After a task's times change (a drag, a resize, the editor), the tasks that wait for it
 * may start before it ends. [plan] says so without writing anything; [push] moves them
 * later, just enough, keeping their lengths (the assistant's rule, [DependentsRescheduler]),
 * through the shared apply path, so it can be undone and never overwrites a newer change.
 * Moving a task earlier never pulls its dependents along.
 */
class PushDependents(
    private val repository: ProjectRepository,
    private val applyChanges: ApplyScheduleChanges,
    private val zone: ZoneId,
) {
    suspend fun plan(projectId: String, taskId: String): DependentsPlan {
        val state = repository.schedule(projectId) ?: return DependentsPlan.NothingToPush
        val title = state.tasks.firstOrNull { it.id == taskId }?.title ?: return DependentsPlan.NothingToPush
        val window = state.project.window(zone)
        return when (val result = DependentsRescheduler.reschedule(state, taskId, window)) {
            is DependentsRescheduler.Result.Moves ->
                if (result.updates.isEmpty()) DependentsPlan.NothingToPush else DependentsPlan.CanPush(title, result.updates.size)
            is DependentsRescheduler.Result.DoesNotFit ->
                DependentsPlan.DoesNotFit(title, titles(state.tasks, result.taskIds), window.endMillis)
        }
    }

    suspend fun push(projectId: String, taskId: String): PushResult {
        val state = repository.schedule(projectId) ?: return PushResult.Refused
        val window = state.project.window(zone)
        return when (val result = DependentsRescheduler.reschedule(state, taskId, window)) {
            is DependentsRescheduler.Result.DoesNotFit -> PushResult.DoesNotFit(titles(state.tasks, result.taskIds), window.endMillis)
            is DependentsRescheduler.Result.Moves -> when {
                result.updates.isEmpty() -> PushResult.NothingToPush
                else -> when (val applied = applyChanges(projectId, result.updates)) {
                    is ApplyResult.Applied -> PushResult.Pushed(result.updates.size, applied.undo)
                    is ApplyResult.Rejected -> PushResult.Refused
                }
            }
        }
    }

    private fun titles(tasks: List<noel.chronova.core.model.SchedulerTask>, ids: List<String>) =
        ids.mapNotNull { id -> tasks.firstOrNull { it.id == id }?.title }
}
