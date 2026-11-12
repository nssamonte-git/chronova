package noel.chronova.core.model

/**
 * An immutable scheduled task belonging to project [projectId]. Times are epoch
 * milliseconds; duration is derived so start/end can never disagree with it.
 * [sortOrder] is the task's row position within its project.
 */
data class SchedulerTask(
    val id: String,
    val projectId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val color: TaskColor = TaskColor.BLUE,
    val sortOrder: Int = 0,
) {
    init {
        require(endMillis > startMillis) { "Task '$id' must end after it starts" }
    }

    val durationMillis: Long get() = endMillis - startMillis
}

/** Semantic task colors; mapped to theme colors in the UI layer. */
enum class TaskColor { BLUE, GREEN, PURPLE, AMBER }
