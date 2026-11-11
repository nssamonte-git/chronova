package noel.chronova.app.domain.model

/**
 * Finish-to-start link: [successorId] should not start before [predecessorId]
 * ends. Both tasks belong to [projectId]. Violations are shown, not prevented.
 */
data class TaskDependency(
    val id: String,
    val projectId: String,
    val predecessorId: String,
    val successorId: String,
) {
    init {
        require(predecessorId != successorId) { "A task can't depend on itself" }
    }

    /** True when the successor starts before the predecessor ends. */
    fun isViolated(predecessor: SchedulerTask, successor: SchedulerTask): Boolean =
        successor.startMillis < predecessor.endMillis
}
