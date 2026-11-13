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
 */
object CriticalPath {
    data class Result(
        /** The latest task end; null for no tasks. */
        val finishMillis: Long?,
        /** Per task id, how far its end could slip without moving the finish (negative when a link is broken). */
        val slackMillis: Map<String, Long>,
    ) {
        fun isCritical(taskId: String): Boolean = (slackMillis[taskId] ?: return false) <= 0L
    }

    fun of(tasks: List<SchedulerTask>, links: List<TaskDependency>): Result {
        if (tasks.isEmpty()) return Result(null, emptyMap())
        val byId = tasks.associateBy { it.id }
        val finish = tasks.maxOf { it.endMillis }
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
            val task = byId.getValue(id)
            val finishBy = successorsOf[id].orEmpty().mapNotNull { latestStart[it] }.minOrNull() ?: finish
            latestFinish[id] = finishBy
            latestStart[id] = finishBy - task.durationMillis
            for (before in predecessorsOf[id].orEmpty()) {
                val left = waitingCount.getValue(before) - 1
                waitingCount[before] = left
                if (left == 0) ready.addLast(before)
            }
        }
        // Tasks left over are in a cycle: measured against the finish alone.
        val slack = tasks.associate { task -> task.id to (latestFinish[task.id] ?: finish) - task.endMillis }
        return Result(finish, slack)
    }
}
