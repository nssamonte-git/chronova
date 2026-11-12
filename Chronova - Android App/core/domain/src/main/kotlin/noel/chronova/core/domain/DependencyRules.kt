package noel.chronova.core.domain

import noel.chronova.core.model.TaskDependency

object DependencyRules {
    /**
     * Tasks that [taskId] may not depend on: itself and every task that already
     * depends on it, directly or through a chain (adding any of them would close a
     * cycle).
     */
    fun forbiddenPredecessors(taskId: String, dependencies: List<TaskDependency>): Set<String> {
        val successorsOf = dependencies.groupBy({ it.predecessorId }, { it.successorId })
        val reached = mutableSetOf(taskId)
        val queue = ArrayDeque(listOf(taskId))
        while (queue.isNotEmpty()) {
            for (next in successorsOf[queue.removeFirst()].orEmpty()) {
                if (reached.add(next)) queue.addLast(next)
            }
        }
        return reached
    }

    /** Whether the link predecessor → successor can be added to [dependencies]. */
    fun canLink(predecessorId: String, successorId: String, dependencies: List<TaskDependency>): Boolean =
        predecessorId !in forbiddenPredecessors(successorId, dependencies) &&
            dependencies.none { it.predecessorId == predecessorId && it.successorId == successorId }
}
