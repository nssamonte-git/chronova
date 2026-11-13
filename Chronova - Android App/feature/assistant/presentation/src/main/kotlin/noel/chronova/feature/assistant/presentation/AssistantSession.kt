package noel.chronova.feature.assistant.presentation

import noel.chronova.feature.assistant.domain.VoiceAction
import noel.chronova.feature.assistant.domain.ViewContext
import kotlinx.coroutines.flow.update
import noel.chronova.core.ui.assistant.AssistantViewAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import noel.chronova.core.domain.schedule.ScheduleChange
import kotlinx.coroutines.flow.MutableStateFlow

/** What the conversation and [AssistantViewModel] share: the state the screen shows, and the scope it runs in. */
internal class AssistantSession(val scope: CoroutineScope, noticeAccepted: Boolean) {
    val ui = MutableStateFlow(AssistantUiState(noticeAccepted = noticeAccepted))
    val message = MutableStateFlow<AssistantMessage?>(null)

    /** The microphone level (0..1) while listening, for the orb's pulse. */
    val level = MutableStateFlow(0f)

    /** Tasks just changed by the assistant, highlighted while it says what changed. */
    val highlight = MutableStateFlow<Set<String>>(emptySet())

    /** Still saying what changed after the orb closed. */
    val explaining = MutableStateFlow(false)

    /** Saying what changed, after the orb has closed. */
    var afterword: Job? = null

    /** What the last applied change's undo would apply, while its message offers Undo. */
    var lastUndo: List<ScheduleChange> = emptyList()

    /** What the screen shows (from [AssistantViewModel.onView]); sent with each request. */
    var view: ViewContext? = null

    /** Things asked to be shown (go to today, show a task), in order, until the screen has done each. */
    val views = MutableStateFlow<List<PendingView>>(emptyList())
    private var viewIds = 0L

    fun show(actions: List<AssistantViewAction>) {
        if (actions.isEmpty()) return
        views.update { pending -> pending + actions.map { PendingView(++viewIds, it) } }
    }

    fun shown(id: Long) {
        views.update { pending -> pending.filterNot { it.id == id } }
    }

    /** Where to go (another screen), once it has said so; the shell navigates and calls [navigated]. */
    val navigation = MutableStateFlow<NavigationUi?>(null)

    fun navigate(action: VoiceAction) {
        navigation.value = when (action) {
            is VoiceAction.OpenProject -> NavigationUi.OpenProject(++viewIds, action.projectId)
            VoiceAction.GoToProjects -> NavigationUi.Projects(++viewIds)
            VoiceAction.OpenNotifications -> NavigationUi.Notifications(++viewIds)
            VoiceAction.OpenAccount -> NavigationUi.Account(++viewIds)
            else -> return
        }
    }

    fun navigated(id: Long) {
        navigation.update { it?.takeUnless { request -> request.id == id } }
    }
}

/** One view action waiting for the screen; [id] tells it from the next. */
internal data class PendingView(val id: Long, val action: AssistantViewAction)
