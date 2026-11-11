package noel.chronova.app.domain.model

/**
 * An immutable scheduled task. Times are epoch milliseconds; duration is derived so
 * start/end can never disagree with it.
 */
data class SchedulerTask(
    val id: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val color: TaskColor = TaskColor.BLUE,
) {
    init {
        require(endMillis > startMillis) { "Task '$id' must end after it starts" }
    }

    val durationMillis: Long get() = endMillis - startMillis
}

/** Semantic task colors; mapped to theme colors in the UI layer. */
enum class TaskColor { BLUE, GREEN, PURPLE, AMBER }
