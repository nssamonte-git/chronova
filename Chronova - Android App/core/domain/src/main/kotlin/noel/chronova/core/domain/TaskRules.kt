package noel.chronova.core.domain

import noel.chronova.core.timeline.TaskEdits
import noel.chronova.core.model.TimelineWindow

/** Why a task can't be saved as entered. */
sealed interface TaskProblem {
    data object BlankTitle : TaskProblem
    data object EndNotAfterStart : TaskProblem
    data class TooShort(val minMinutes: Int) : TaskProblem
    data object OutsideProject : TaskProblem
}

object TaskRules {
    /** The first problem with the entered values, or null when they can be saved. */
    fun validate(title: String, startMillis: Long, endMillis: Long, window: TimelineWindow): TaskProblem? {
        if (title.isBlank()) return TaskProblem.BlankTitle
        if (endMillis <= startMillis) return TaskProblem.EndNotAfterStart
        if (endMillis - startMillis < TaskEdits.MIN_DURATION_MILLIS) {
            return TaskProblem.TooShort((TaskEdits.MIN_DURATION_MILLIS / 60_000L).toInt())
        }
        if (startMillis < window.startMillis || endMillis > window.endMillis) return TaskProblem.OutsideProject
        return null
    }
}
