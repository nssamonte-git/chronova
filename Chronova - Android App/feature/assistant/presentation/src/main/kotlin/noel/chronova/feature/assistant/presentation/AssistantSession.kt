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

    /** The project it just made, while its message offers Undo (which deletes it). */
    var lastProjectId: String? = null

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

    /** The app's to do (go elsewhere, a setting, sync, export), in order, until the shell has done each. */
    val appRequests = MutableStateFlow<List<AppRequestUi>>(emptyList())

    fun request(action: VoiceAction) {
        val id = ++viewIds
        val request = when (action) {
            is VoiceAction.OpenProject -> AppRequestUi.OpenProject(id, action.projectId)
            VoiceAction.GoToProjects -> AppRequestUi.Projects(id)
            VoiceAction.OpenNotifications -> AppRequestUi.Notifications(id)
            VoiceAction.OpenAccount -> AppRequestUi.Account(id)
            is VoiceAction.SetTheme -> AppRequestUi.Theme(id, action.theme)
            is VoiceAction.SetReminders -> AppRequestUi.Reminders(id, action.lead)
            VoiceAction.SyncNow -> AppRequestUi.Sync(id)
            is VoiceAction.Export -> AppRequestUi.Export(id, action.projectId, action.projectName, action.format.name, action.notes)
            else -> return
        }
        appRequests.update { it + request }
    }

    fun requestDone(id: Long) {
        appRequests.update { pending -> pending.filterNot { it.id == id } }
    }
}

/** One view action waiting for the screen; [id] tells it from the next. */
internal data class PendingView(val id: Long, val action: AssistantViewAction)
