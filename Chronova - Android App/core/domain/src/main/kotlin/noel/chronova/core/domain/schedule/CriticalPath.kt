package noel.chronova.core.domain.schedule

import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency

/**
 * The critical path of a schedule as it is: the tasks where any delay moves the
 * project's finish (the latest task end). Works on the scheduled dates and the
 * finish-to-start links: going back from the finish, a task's latest finish is the
 * earliest latest start of the tasks that wait for it (or the finish when none do), and
 * its slack is how far its end could slip before that. Slack 0 or less is critical;
 * a broken link gives negative slack. Links in a cycle (the editor prevents them) are ignored.
 *
 * With [WorkingTime], slack is working time: a night or a weekend between two tasks is no
 * slack, so a chain whose tasks each start the working morning after the one before is
 * critical. A task scheduled wholly outside working hours (a Saturday, an evening) is
 * still measured by the clock, since those are the hours it is worked in.
 */
object CriticalPath {
    data class Result(
        /** The latest task end; null for no tasks. */
        val finishMillis: Long?,
        /** Per task id, how far its end could slip without moving the finish (negative when a link is broken). */
        val slackMillis: Map<String, Long>,
        /** The tasks whose slack is working time; the others' is time by the clock. */
        val inWorkingTime: Set<String> = emptySet(),
    ) {
        fun isCritical(taskId: String): Boolean = (slackMillis[taskId] ?: return false) <= 0L
    }

    /** [working]: the hours that count as work; null measures everything by the clock. */
    fun of(tasks: List<SchedulerTask>, links: List<TaskDependency>, working: WorkingTime? = null): Result {
        if (tasks.isEmpty()) return Result(null, emptyMap())
        val finish = tasks.maxOf { it.endMillis }
        val byTheClock = slack(tasks, links) { it }
        if (working == null) return Result(finish, byTheClock)

        val ruler = working.ruler(tasks.minOf { it.startMillis }, finish)
        val worked = slack(tasks, links, ruler::workedBefore)
        // Work is what has working time in it; a milestone, having no length, when it stands in working hours.
        val inWorkingTime = tasks.filter { task ->
            if (task.endMillis == task.startMillis) {
                ruler.isWorking(task.startMillis)
            } else {
                ruler.workedBefore(task.endMillis) > ruler.workedBefore(task.startMillis)
            }
        }.mapTo(HashSet()) { it.id }
        val slack = tasks.associate { task -> task.id to (if (task.id in inWorkingTime) worked else byTheClock).getValue(task.id) }
        return Result(finish, slack, inWorkingTime)
    }

    /** Every task's slack, with times measured by [at] (the clock itself, or the working time before a moment). */
    private fun slack(tasks: List<SchedulerTask>, links: List<TaskDependency>, at: (Long) -> Long): Map<String, Long> {
        val byId = tasks.associateBy { it.id }
        val start = tasks.associate { it.id to at(it.startMillis) }
        val end = tasks.associate { it.id to at(it.endMillis) }
        val finish = end.values.max()
        val live = links.filter { it.predecessorId in byId && it.successorId in byId }
        val successorsOf = live.groupBy({ it.predecessorId }, { it.successorId })
        val predecessorsOf = live.groupBy({ it.successorId }, { it.predecessorId })

        // Reverse dependency order: a task comes after everything that waits for it (Kahn from the ends).
        val waitingCount = tasks.associateTo(HashMap()) { it.id to successorsOf[it.id].orEmpty().size }
        val ready = ArrayDeque(tasks.filter { waitingCount[it.id] == 0 }.map { it.id })
        val latestStart = HashMap<String, Long>()
        val latestFinish = HashMap<String, Long>()
        while (ready.isNotEmpty()) {
            val id = ready.removeFirst()
            val finishBy = successorsOf[id].orEmpty().mapNotNull { latestStart[it] }.minOrNull() ?: finish
            latestFinish[id] = finishBy
            latestStart[id] = finishBy - (end.getValue(id) - start.getValue(id))
            for (before in predecessorsOf[id].orEmpty()) {
                val left = waitingCount.getValue(before) - 1
                waitingCount[before] = left
                if (left == 0) ready.addLast(before)
            }
        }
        // Tasks left over are in a cycle: measured against the finish alone.
        return tasks.associate { task -> task.id to (latestFinish[task.id] ?: finish) - end.getValue(task.id) }
    }
}
