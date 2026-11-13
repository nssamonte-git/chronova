package noel.chronova.core.model

/**
 * An immutable scheduled task belonging to project [projectId]. Times are epoch
 * milliseconds; duration is derived so start/end can never disagree with it.
 * [sortOrder] is the task's row position within its project.
 *
 * [progress] is the percent complete (0–100; 100 is done). A milestone
 * ([isMilestone]) is a moment rather than a span: it ends when it starts, and it is
 * either not done or done (progress 0 or 100). [notes] is free text; its length
 * limit is a save rule (`TaskRules`), not part of the model.
 */
data class SchedulerTask(
    val id: String,
    val projectId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val color: TaskColor = TaskColor.BLUE,
    val sortOrder: Int = 0,
    val progress: Int = 0,
    val notes: String = "",
    val isMilestone: Boolean = false,
) {
    init {
        if (isMilestone) {
            require(endMillis == startMillis) { "Milestone '$id' must end when it starts" }
            require(progress == 0 || progress == DONE) { "Milestone '$id' is either done or not" }
        } else {
            require(endMillis > startMillis) { "Task '$id' must end after it starts" }
        }
        require(progress in 0..DONE) { "Task '$id' progress must be 0–100" }
    }

    val durationMillis: Long get() = endMillis - startMillis

    val isDone: Boolean get() = progress == DONE

    companion object {
        /** The progress of a finished task. */
        const val DONE = 100
    }
}

/** Semantic task colors; mapped to theme colors in the UI layer. */
enum class TaskColor { BLUE, GREEN, PURPLE, AMBER }
