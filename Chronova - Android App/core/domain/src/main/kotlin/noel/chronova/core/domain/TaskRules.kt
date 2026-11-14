package noel.chronova.core.domain

import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.timeline.TaskEdits
import noel.chronova.core.model.TimelineWindow

/** Why a task can't be saved as entered. */
sealed interface TaskProblem {
    data object BlankTitle : TaskProblem
    data object EndNotAfterStart : TaskProblem
    data class TooShort(val minMinutes: Int) : TaskProblem
    data object OutsideProject : TaskProblem
    data class NotesTooLong(val maxLength: Int) : TaskProblem

    /** Progress outside 0–100, or a milestone that is neither done nor not done. */
    data object BadProgress : TaskProblem
}

object TaskRules {
    /** The longest notes a task can keep (the sync protocol's limit). */
    const val MAX_NOTES_LENGTH = 2000

    /** The shortest a task can be, in minutes. */
    val MIN_MINUTES: Int = (TaskEdits.MIN_DURATION_MILLIS / 60_000L).toInt()

    /**
     * The first problem with the entered values, or null when they can be saved.
     * A milestone has no length: it must end when it starts, and that moment must
     * fall inside the project (before its end).
     */
    fun validate(
        title: String,
        startMillis: Long,
        endMillis: Long,
        window: TimelineWindow,
        isMilestone: Boolean = false,
        progress: Int = 0,
        notes: String = "",
    ): TaskProblem? {
        if (title.isBlank()) return TaskProblem.BlankTitle
        if (notes.length > MAX_NOTES_LENGTH) return TaskProblem.NotesTooLong(MAX_NOTES_LENGTH)
        if (progress !in 0..SchedulerTask.DONE) return TaskProblem.BadProgress
        if (isMilestone) {
            if (progress != 0 && progress != SchedulerTask.DONE) return TaskProblem.BadProgress
            if (endMillis != startMillis) return TaskProblem.EndNotAfterStart
            if (startMillis < window.startMillis || startMillis >= window.endMillis) return TaskProblem.OutsideProject
            return null
        }
        if (endMillis <= startMillis) return TaskProblem.EndNotAfterStart
        if (endMillis - startMillis < TaskEdits.MIN_DURATION_MILLIS) {
            return TaskProblem.TooShort(MIN_MINUTES)
        }
        if (startMillis < window.startMillis || endMillis > window.endMillis) return TaskProblem.OutsideProject
        return null
    }

    /** [validate] for a whole task. */
    fun validate(task: SchedulerTask, window: TimelineWindow): TaskProblem? = validate(
        task.title, task.startMillis, task.endMillis, window, task.isMilestone, task.progress, task.notes,
    )
}
