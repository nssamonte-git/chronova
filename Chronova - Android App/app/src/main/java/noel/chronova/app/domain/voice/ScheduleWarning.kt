package noel.chronova.app.domain.voice

import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.schedule.ScheduleChange
import noel.chronova.app.domain.schedule.ScheduleState

/**
 * A clash a proposed change would cause, so the assistant can say so before
 * asking whether to apply it anyway. Clashes are allowed (the timeline shows
 * them), so these only inform the user's choice.
 */
sealed interface ScheduleWarning {
    /** [task], which the change moves or adds, would overlap [other] in time. */
    data class Overlap(val task: String, val other: String) : ScheduleWarning

    /** [successor] would start before [predecessor], which it waits for, ends. */
    data class BrokenLink(val predecessor: String, val successor: String) : ScheduleWarning

    companion object {
        /**
         * The clashes [changes] would cause in [state]: overlaps involving a task
         * the changes move or add, and links that would be broken, in timeline
         * order. Clashes that are already there are not repeated, and a broken
         * link isn't also reported as an overlap of the same two tasks.
         */
        fun from(changes: List<ScheduleChange>, state: ScheduleState): List<ScheduleWarning> {
            val before = state.tasks.associateBy { it.id }
            val after = before.toMutableMap()
            val links = state.links.associateBy { it.id }.toMutableMap()
            val touched = linkedSetOf<String>()
            val newLinks = mutableSetOf<String>()
            changes.forEach { change ->
                when (change) {
                    is ScheduleChange.UpdateTask -> {
                        after[change.after.id] = change.after
                        if (change.before.startMillis != change.after.startMillis || change.before.endMillis != change.after.endMillis) {
                            touched += change.after.id
                        }
                    }
                    is ScheduleChange.CreateTask -> {
                        after[change.task.id] = change.task
                        touched += change.task.id
                    }
                    is ScheduleChange.DeleteTask -> after.remove(change.task.id)
                    is ScheduleChange.AddLink -> {
                        links[change.link.id] = change.link
                        newLinks += change.link.id
                    }
                    is ScheduleChange.RemoveLink -> links.remove(change.link.id)
                }
            }

            val order = compareBy<SchedulerTask>({ it.startMillis }, { it.sortOrder }, { it.id })
            val brokenPairs = mutableSetOf<Set<String>>()
            val broken = links.values.mapNotNull { link ->
                val predecessor = after[link.predecessorId] ?: return@mapNotNull null
                val successor = after[link.successorId] ?: return@mapNotNull null
                if (!link.isViolated(predecessor, successor)) return@mapNotNull null
                val wasBroken = link.id !in newLinks && before[link.predecessorId]?.let { p ->
                    before[link.successorId]?.let { s -> link.isViolated(p, s) }
                } == true
                if (wasBroken) return@mapNotNull null
                brokenPairs += setOf(predecessor.id, successor.id)
                successor to BrokenLink(predecessor.title, successor.title)
            }.sortedWith(compareBy(order) { it.first }).map { it.second }

            val overlaps = mutableListOf<ScheduleWarning>()
            val seen = brokenPairs.toMutableSet()
            touched.mapNotNull { after[it] }.sortedWith(order).forEach { task ->
                after.values.filter { it.id != task.id && overlap(task, it) }.sortedWith(order).forEach { other ->
                    val pair = setOf(task.id, other.id)
                    val already = before[task.id]?.let { was -> before[other.id]?.let { overlap(was, it) } } == true
                    if (!already && seen.add(pair)) overlaps += Overlap(task.title, other.title)
                }
            }

            return overlaps + broken
        }

        private fun overlap(a: SchedulerTask, b: SchedulerTask) = a.startMillis < b.endMillis && b.startMillis < a.endMillis
    }
}
