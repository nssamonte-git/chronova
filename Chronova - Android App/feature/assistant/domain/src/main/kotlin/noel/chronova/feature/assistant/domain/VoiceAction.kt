package noel.chronova.feature.assistant.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException
import noel.chronova.core.domain.schedule.ScheduleState

/** One proposed action as the server sent it; which fields are set depends on [type]. */
data class ProposedAction(
    val type: String,
    val date: String? = null,
    val taskId: String? = null,
)

/**
 * Something the app does at once because it was asked to, without changing anything
 * (`docs/voice-api.md`, "actions"): unlike schedule edits it needs no spoken yes.
 */
sealed interface VoiceAction {
    data object GoToToday : VoiceAction
    data class GoToDate(val date: LocalDate) : VoiceAction

    /** Selects the task and scrolls to it; [title] is for what the assistant says. */
    data class ShowTask(val taskId: String, val title: String) : VoiceAction

    companion object {
        /** The action for [proposed] on [state], or null if it is malformed or names a task that isn't there. */
        fun from(proposed: ProposedAction, state: ScheduleState): VoiceAction? = try {
            when (proposed.type) {
                "go_to_today" -> GoToToday
                "go_to_date" -> GoToDate(LocalDate.parse(proposed.date ?: return null))
                "show_task" -> state.tasks.firstOrNull { it.id == proposed.taskId }?.let { ShowTask(it.id, it.title) }
                else -> null
            }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
