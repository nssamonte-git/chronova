package noel.chronova.feature.assistant.domain

import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.domain.schedule.ScheduleState
import noel.chronova.core.domain.schedule.WorkingTime
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency

/**
 * Whether a clash-free alternative the model suggested is one worth offering, beyond being valid
 * and clash-free (seen while recording the walkthrough, 14 Nov 2026: a three-day task moved out of
 * the way of a three-hour one, three tasks moved for one clash, a tasting offered at 06:00):
 * - it moves, adds or deletes only what the request did, plus tasks that wait (through links) for
 *   one of those, which is what pushing dependents does;
 * - a task inside working hours ([WorkingTime]) as the request left it, or as it is when the request
 *   doesn't move it, stays inside them. Work the request put outside them (a Saturday, whole days)
 *   may stay outside.
 * New tasks are matched by title, since each plan gives them new ids.
 */
class SensibleAlternative(private val working: WorkingTime) {
    fun accepts(planned: List<ScheduleChange>, alternative: List<ScheduleChange>, state: ScheduleState): Boolean {
        val wanted = Outcome(planned, state)
        val offered = Outcome(alternative, state)
        if (offered.deleted.any { it !in wanted.deleted }) return false

        val allowed = wanted.moved.keys + waitingFor(wanted.moved.keys, offered.links)
        val unasked = offered.moved.keys.filter { it !in allowed }
        val newTitles = wanted.created.map { it.title }.toSet()
        if (unasked.any { id -> id !in offered.createdIds || offered.moved.getValue(id).title !in newTitles }) return false

        val times = offered.moved.values.flatMap { listOf(it.startMillis, it.endMillis) } +
            wanted.moved.values.flatMap { listOf(it.startMillis, it.endMillis) } +
            state.tasks.flatMap { listOf(it.startMillis, it.endMillis) }
        val ruler = working.ruler(times.min(), times.max())
        fun inHours(task: SchedulerTask): Boolean = if (task.isMilestone) {
            ruler.isWorking(task.startMillis)
        } else {
            ruler.isWorking(task.startMillis) && ruler.isWorking(task.endMillis) &&
                ruler.workedBefore(task.endMillis) > ruler.workedBefore(task.startMillis)
        }
        return offered.moved.values.all { task ->
            val reference = wanted.moved[task.id]
                ?: wanted.created.firstOrNull { it.title == task.title }
                ?: state.tasks.firstOrNull { it.id == task.id }
            reference == null || !inHours(reference) || inHours(task)
        }
    }

    /** Tasks that wait, directly or through others, for any of [ids]. */
    private fun waitingFor(ids: Set<String>, links: Collection<TaskDependency>): Set<String> {
        val after = links.groupBy({ it.predecessorId }, { it.successorId })
        val found = mutableSetOf<String>()
        val next = ArrayDeque(ids)
        while (next.isNotEmpty()) {
            for (successor in after[next.removeFirst()].orEmpty()) if (found.add(successor)) next += successor
        }
        return found
    }

    /** What a change list does to the schedule: tasks given new times or added, deleted, and the links after it. */
    private class Outcome(changes: List<ScheduleChange>, state: ScheduleState) {
        val moved = LinkedHashMap<String, SchedulerTask>()
        val created = mutableListOf<SchedulerTask>()
        val createdIds = mutableSetOf<String>()
        val deleted = mutableSetOf<String>()
        val links: Collection<TaskDependency>

        init {
            val all = state.links.associateBy { it.id }.toMutableMap()
            for (change in changes) {
                when (change) {
                    is ScheduleChange.UpdateTask -> if (change.before.startMillis != change.after.startMillis || change.before.endMillis != change.after.endMillis) {
                        moved[change.after.id] = change.after
                    }
                    is ScheduleChange.CreateTask -> {
                        moved[change.task.id] = change.task
                        created += change.task
                        createdIds += change.task.id
                    }
                    is ScheduleChange.DeleteTask -> deleted += change.task.id
                    is ScheduleChange.AddLink -> all[change.link.id] = change.link
                    is ScheduleChange.RemoveLink -> all.remove(change.link.id)
                }
            }
            links = all.values
        }
    }
}
