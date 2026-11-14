package noel.chronova.feature.timeline.presentation

import noel.chronova.core.model.TaskColor
import noel.chronova.core.timeline.TimeSpan

/**
 * One task on the timeline, as its row and bar show it. Times are epoch millis.
 * [progress] is percent complete (100 is done); a milestone ([isMilestone]) is a
 * moment, so it ends when it starts.
 */
data class TaskUi(
    val id: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val color: TaskColor = TaskColor.BLUE,
    val progress: Int = 0,
    val notes: String = "",
    val isMilestone: Boolean = false,
    /**
     * How far the task's end could slip without moving the project's finish (see
     * [noel.chronova.core.domain.schedule.CriticalPath]); negative when a link is broken.
     */
    val slackMillis: Long? = null,
    /** [slackMillis] is working time (nights and weekends don't count), not time by the clock. */
    val slackInWorkingTime: Boolean = false,
) {
    val durationMillis: Long get() = endMillis - startMillis

    /** On the critical path: any delay moves the project's finish. */
    val critical: Boolean get() = slackMillis != null && slackMillis <= 0L

    val isDone: Boolean get() = progress == DONE

    /** The task's times, as the gesture engine edits them. */
    val span: TimeSpan get() = TimeSpan(startMillis, endMillis)

    fun withSpan(span: TimeSpan): TaskUi = copy(startMillis = span.startMillis, endMillis = span.endMillis)

    companion object {
        const val DONE = 100
    }
}

/**
 * A finish-to-start link: [successorId] waits for [predecessorId]. [violated]: the successor
 * starts before the predecessor ends, so the arrow is drawn as a warning.
 */
data class DependencyUi(
    val predecessorId: String,
    val successorId: String,
    val violated: Boolean = false,
    /** Both ends are on the critical path. */
    val critical: Boolean = false,
)

/** The editor's line about how much a saved task can slip. */
sealed interface SlackUi {
    /** Any delay moves the project's finish, at [finishMillis]. */
    data class OnCriticalPath(val finishMillis: Long) : SlackUi

    /** The task can end up to [millis] later without delaying the finish; [working]: of working time. */
    data class CanSlip(val millis: Long, val working: Boolean = false) : SlackUi
}

/** Why the entered task can't be saved, as the editor words it. */
sealed interface TaskProblemUi {
    data object BlankTitle : TaskProblemUi
    data object EndNotAfterStart : TaskProblemUi
    data class TooShort(val minMinutes: Int) : TaskProblemUi
    data object OutsideProject : TaskProblemUi
    data class NotesTooLong(val maxLength: Int) : TaskProblemUi
    data object BadProgress : TaskProblemUi
}
