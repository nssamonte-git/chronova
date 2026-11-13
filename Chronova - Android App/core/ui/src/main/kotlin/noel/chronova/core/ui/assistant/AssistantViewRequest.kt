package noel.chronova.core.ui.assistant

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * Something the assistant asks the screen to show, done at once (it changes nothing).
 * [key] tells one request from the next, so a screen handles each once however often it
 * recomposes; the screen calls [onHandled] when it is done (or can't do it).
 */
@Immutable
class AssistantViewRequest(
    val key: Any,
    val action: AssistantViewAction,
    val onHandled: () -> Unit,
)

/** What a screen can be asked to show. */
sealed interface AssistantViewAction {
    /** Scroll to now (or the nearer end of the project when today is outside it). */
    data object GoToToday : AssistantViewAction

    /** Scroll to [date] (in the project; a day outside it goes to the nearer end). */
    data class GoToDate(val date: LocalDate) : AssistantViewAction

    /** Select the task and scroll to it. */
    data class ShowTask(val taskId: String) : AssistantViewAction

    // How the timeline is shown: as if the settings row had been used (remembered for the project).
    data class SetScale(val scale: ViewScale) : AssistantViewAction
    data class Zoom(val step: ViewZoom) : AssistantViewAction
    data class RowHeight(val bigger: Boolean) : AssistantViewAction
    data class CriticalPath(val on: Boolean) : AssistantViewAction
}

enum class ViewScale { HOURS, DAYS, WEEKS, MONTHS }

enum class ViewZoom { IN, OUT, RESET }
