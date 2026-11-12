package noel.chronova.feature.assistant.domain

import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.domain.schedule.ScheduleState

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
            val outcome = Outcome(changes, state)
            val broken = outcome.newlyBroken()
            val brokenPairs = broken.map { (link, _) -> setOf(link.predecessorId, link.successorId) }.toSet()

            val overlaps = mutableListOf<ScheduleWarning>()
            val seen = brokenPairs.toMutableSet()
            outcome.touched.mapNotNull { outcome.after[it] }.sortedWith(ORDER).forEach { task ->
                outcome.after.values.filter { it.id != task.id && overlap(task, it) }.sortedWith(ORDER).forEach { other ->
                    val pair = setOf(task.id, other.id)
                    val already = outcome.before[task.id]?.let { was -> outcome.before[other.id]?.let { overlap(was, it) } } == true
                    if (!already && seen.add(pair)) overlaps += Overlap(task.title, other.title)
                }
            }

            return overlaps + broken.map { (_, warning) -> warning }
        }

        /**
         * The tasks whose waiting tasks [changes] would make start too early: pushing
         * what waits for each of them ("reschedule_dependents") fixes every
         * [BrokenLink] that [from] reports. Only tasks that already exist, in timeline order.
         */
        fun waitedFor(changes: List<ScheduleChange>, state: ScheduleState): List<String> {
            val outcome = Outcome(changes, state)
            return outcome.newlyBroken().map { (link, _) -> link.predecessorId }.distinct()
                .filter { id -> state.tasks.any { it.id == id } }
                .sortedWith(compareBy(ORDER) { outcome.after.getValue(it) })
        }

        private val ORDER = compareBy<SchedulerTask>({ it.startMillis }, { it.sortOrder }, { it.id })

        /** The schedule before and after [changes]. */
        private class Outcome(changes: List<ScheduleChange>, state: ScheduleState) {
            val before = state.tasks.associateBy { it.id }
            val after = before.toMutableMap()
            val links = state.links.associateBy { it.id }.toMutableMap()

            /** Tasks the changes move or add. */
            val touched = linkedSetOf<String>()
            private val newLinks = mutableSetOf<String>()

            init {
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
            }

            /** Links broken after the changes that weren't before, with their warnings, in timeline order. */
            fun newlyBroken(): List<Pair<TaskDependency, BrokenLink>> = links.values.mapNotNull { link ->
                val predecessor = after[link.predecessorId] ?: return@mapNotNull null
                val successor = after[link.successorId] ?: return@mapNotNull null
                if (!link.isViolated(predecessor, successor)) return@mapNotNull null
                val wasBroken = link.id !in newLinks && before[link.predecessorId]?.let { p ->
                    before[link.successorId]?.let { s -> link.isViolated(p, s) }
                } == true
                if (wasBroken) null else Triple(successor, link, BrokenLink(predecessor.title, successor.title))
            }.sortedWith(compareBy(ORDER) { it.first }).map { it.second to it.third }
        }

        private fun overlap(a: SchedulerTask, b: SchedulerTask) = a.startMillis < b.endMillis && b.startMillis < a.endMillis
    }
}
