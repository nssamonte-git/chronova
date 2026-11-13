package noel.chronova.feature.assistant.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException
import noel.chronova.core.domain.schedule.ScheduleState
import noel.chronova.core.model.TimeScale

/** One proposed action as the server sent it; which fields are set depends on [type]. */
data class ProposedAction(
    val type: String,
    val date: String? = null,
    val taskId: String? = null,
    /** `set_scale`: hours, days, weeks or months. */
    val scale: String? = null,
    /** `zoom`: in, out or reset. */
    val direction: String? = null,
    /** `row_height`: bigger or smaller. */
    val size: String? = null,
    /** `critical_path`: show it or hide it. */
    val on: Boolean? = null,
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

    // How the timeline is shown (remembered for the project, like the settings row); never task times.
    data class SetScale(val scale: TimeScale) : VoiceAction
    data class Zoom(val step: ZoomStep) : VoiceAction
    data class RowHeight(val bigger: Boolean) : VoiceAction
    data class CriticalPath(val on: Boolean) : VoiceAction

    enum class ZoomStep { IN, OUT, RESET }

    companion object {
        /** The action for [proposed] on [state], or null if it is malformed or names a task that isn't there. */
        fun from(proposed: ProposedAction, state: ScheduleState): VoiceAction? = try {
            when (proposed.type) {
                "go_to_today" -> GoToToday
                "go_to_date" -> GoToDate(LocalDate.parse(proposed.date ?: return null))
                "show_task" -> state.tasks.firstOrNull { it.id == proposed.taskId }?.let { ShowTask(it.id, it.title) }
                "set_scale" -> TimeScale.entries.firstOrNull { it.name.equals(proposed.scale, ignoreCase = true) }?.let(::SetScale)
                "zoom" -> ZoomStep.entries.firstOrNull { it.name.equals(proposed.direction, ignoreCase = true) }?.let(::Zoom)
                "row_height" -> when (proposed.size) {
                    "bigger" -> RowHeight(bigger = true)
                    "smaller" -> RowHeight(bigger = false)
                    else -> null
                }
                "critical_path" -> proposed.on?.let(::CriticalPath)
                else -> null
            }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
