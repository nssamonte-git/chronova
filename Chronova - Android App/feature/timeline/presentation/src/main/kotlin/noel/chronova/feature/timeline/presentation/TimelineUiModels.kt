package noel.chronova.feature.timeline.presentation

import noel.chronova.core.model.TaskColor
import noel.chronova.core.timeline.TimeSpan

/** One task on the timeline, as its row and bar show it. Times are epoch millis. */
data class TaskUi(
    val id: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val color: TaskColor = TaskColor.BLUE,
) {
    val durationMillis: Long get() = endMillis - startMillis

    /** The task's times, as the gesture engine edits them. */
    val span: TimeSpan get() = TimeSpan(startMillis, endMillis)

    fun withSpan(span: TimeSpan): TaskUi = copy(startMillis = span.startMillis, endMillis = span.endMillis)
}

/**
 * A finish-to-start link: [successorId] waits for [predecessorId]. [violated]: the successor
 * starts before the predecessor ends, so the arrow is drawn as a warning.
 */
data class DependencyUi(val predecessorId: String, val successorId: String, val violated: Boolean = false)

/** Why the entered task can't be saved, as the editor words it. */
sealed interface TaskProblemUi {
    data object BlankTitle : TaskProblemUi
    data object EndNotAfterStart : TaskProblemUi
    data class TooShort(val minMinutes: Int) : TaskProblemUi
    data object OutsideProject : TaskProblemUi
}
