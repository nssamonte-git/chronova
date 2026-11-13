package noel.chronova.core.domain.schedule

import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TimelineWindow

/**
 * Works out how far to push the tasks that depend on one task, so every link in
 * that chain is satisfied. Tasks only ever move later, keep their duration, and
 * move just enough to start when their latest predecessor ends. Used by voice
 * commands and by the timeline's "Push" offer after a change; a drag itself never
 * moves other tasks.
 */
object DependentsRescheduler {

    sealed interface Result {
        /** The tasks that move (as before → after), in dependency order; empty when nothing needs to move. */
        data class Moves(val updates: List<ScheduleChange.UpdateTask>) : Result

        /** These tasks would end after the project ends; nothing is moved. */
        data class DoesNotFit(val taskIds: List<String>) : Result
    }

    fun reschedule(state: ScheduleState, taskId: String, window: TimelineWindow): Result {
        val successorsOf = state.links.groupBy({ it.predecessorId }, { it.successorId })
        val predecessorsOf = state.links.groupBy({ it.successorId }, { it.predecessorId })

        // Everything downstream of the task.
        val downstream = linkedSetOf<String>()
        val queue = ArrayDeque(listOf(taskId))
        while (queue.isNotEmpty()) {
            for (next in successorsOf[queue.removeFirst()].orEmpty()) {
                if (next != taskId && downstream.add(next)) queue.addLast(next)
            }
        }

        // Dependency order within that set (Kahn); tasks caught in a cycle are left alone.
        val pending = downstream.associateWithTo(HashMap()) { id ->
            predecessorsOf[id].orEmpty().count { it in downstream }
        }
        val ready = ArrayDeque(pending.filterValues { it == 0 }.keys.sortedBy { id -> state.tasks.indexOfFirst { it.id == id } })
        val tasks = state.tasks.associateByTo(HashMap()) { it.id }
        val moves = mutableListOf<ScheduleChange.UpdateTask>()
        val doesNotFit = mutableListOf<String>()
        while (ready.isNotEmpty()) {
            val id = ready.removeFirst()
            val task = tasks[id]
            if (task != null) {
                val earliestStart = predecessorsOf[id].orEmpty().mapNotNull { tasks[it]?.endMillis }.maxOrNull()
                if (earliestStart != null && task.startMillis < earliestStart) {
                    val moved = task.shiftedTo(earliestStart)
                    if (moved.endMillis > window.endMillis) doesNotFit += id
                    tasks[id] = moved
                    moves += ScheduleChange.UpdateTask(task, moved)
                }
            }
            for (next in successorsOf[id].orEmpty()) {
                val left = pending[next] ?: continue
                pending[next] = left - 1
                if (left - 1 == 0) ready.addLast(next)
            }
        }
        return if (doesNotFit.isEmpty()) Result.Moves(moves) else Result.DoesNotFit(doesNotFit)
    }

    private fun SchedulerTask.shiftedTo(start: Long) = copy(startMillis = start, endMillis = start + durationMillis)
}
