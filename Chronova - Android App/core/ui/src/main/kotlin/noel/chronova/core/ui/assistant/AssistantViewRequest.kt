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
}
