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

    /** Open the search bar with these words and chips, listing only the matches. */
    data class Search(val query: String?, val colors: Set<ViewColor>, val kinds: Set<ViewKind>) : AssistantViewAction

    /** Close the search: every task again. */
    data object ClearSearch : AssistantViewAction

    /** Open the task's editor. */
    data class OpenTask(val taskId: String) : AssistantViewAction
}

/** The search bar's colour chips (the task colours) and its kind chips. */
enum class ViewColor { BLUE, GREEN, PURPLE, AMBER }

enum class ViewKind { NOT_DONE, DONE, MILESTONE }

enum class ViewScale { HOURS, DAYS, WEEKS, MONTHS }

enum class ViewZoom { IN, OUT, RESET }

/** What the screen shows, told to the assistant so it understands "this task" or "zoom in". */
@Immutable
data class AssistantViewState(
    val scale: ViewScale,
    val criticalPath: Boolean,
    val selectedTaskId: String?,
    /** The open search, or null when none narrows the list. */
    val search: AssistantSearchState? = null,
)

@Immutable
data class AssistantSearchState(val query: String?, val colors: Set<ViewColor>, val kinds: Set<ViewKind>)
