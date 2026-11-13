package noel.chronova.feature.assistant.presentation

import noel.chronova.core.ui.assistant.AssistantViewState
import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.feature.assistant.domain.AssistantUseCases
import noel.chronova.feature.assistant.domain.VoiceAvailability
import noel.chronova.feature.assistant.presentation.orb.OrbMode

/**
 * The voice-only assistant on the timeline: whether it can be used, opening and closing the orb,
 * the first-open introduction, undo and messages. What is said and done while the orb is open is
 * [AssistantConversation]. Nothing is written without a spoken yes; tapping outside the orb stops
 * everything.
 */
class AssistantViewModel(
    /** The open project, or null on the list of projects (only where to go can be asked there). */
    private val projectId: String?,
    private val useCases: AssistantUseCases,
    phrases: AssistantPhrases,
    zone: ZoneId = ZoneId.systemDefault(),
    /** A hint for the transcriber: the phone's language ("en", "fil"), or null to let it detect. */
    language: () -> String? = { Locale.getDefault().toLanguageTag().substringBefore('-').ifBlank { null } },
) : ViewModel() {

    private val session = AssistantSession(viewModelScope, noticeAccepted = useCases.notice.accepted)
    private val conversation = AssistantConversation(projectId, useCases, phrases, zone, language, session)
    private val ui = session.ui

    /** The open orb's conversation, or the first-open introduction. */
    private var talking: Job? = null

    val state: StateFlow<AssistantUiState> = combine(ui, useCases.observeAvailability()) { ui, availability ->
        ui.copy(
            availability = when (availability) {
                VoiceAvailability.NOT_CONFIGURED -> AssistantAvailability.NOT_CONFIGURED
                VoiceAvailability.SIGNED_OUT -> AssistantAvailability.SIGNED_OUT
                VoiceAvailability.OFFLINE -> AssistantAvailability.OFFLINE
                VoiceAvailability.READY -> AssistantAvailability.READY
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssistantUiState())

    val message: StateFlow<AssistantMessage?> = session.message.asStateFlow()

    /** The microphone level (0..1) while listening, for the orb's pulse. */
    val level: StateFlow<Float> = session.level.asStateFlow()

    /** Tasks just changed by the assistant, highlighted while it says what changed. */
    val highlight: StateFlow<Set<String>> = session.highlight.asStateFlow()

    /** The next thing it asked the screen to show, until the screen calls [viewShown]. */
    val view: StateFlow<ViewRequestUi?> = session.views
        .map { pending -> pending.firstOrNull()?.let { ViewRequestUi(it.id, it.action) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The screen has shown request [id]. */
    fun viewShown(id: Long) = session.shown(id)

    /** Another screen it was asked to go to, until the shell calls [navigated]. */
    val navigation: StateFlow<NavigationUi?> = session.navigation.asStateFlow()

    fun navigated(id: Long) = session.navigated(id)

    /** What the screen shows now; sent with the next request. */
    fun onView(view: AssistantViewState) {
        session.view = view.toDomain()
    }

    /**
     * The assistant is at work: the orb is open (listening, thinking, talking, applying) or, once it
     * has closed, it is still saying what it changed. The screen keeps its orientation meanwhile.
     */
    val busy: StateFlow<Boolean> = combine(ui, session.explaining) { ui, explaining -> ui.open || explaining }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun acceptNotice() {
        useCases.notice.accept()
        ui.update { it.copy(noticeAccepted = true) }
    }

    /** Checking the server after a tap, before the orb opens. */
    private var connecting: Job? = null

    /**
     * Opens the orb and starts listening (the screen has the microphone permission by now),
     * once the server answers; if it doesn't, the orb stays closed and the user is told.
     */
    fun open() {
        if (ui.value.open || connecting?.isActive == true) return
        when (state.value.availability) {
            AssistantAvailability.READY -> Unit
            AssistantAvailability.OFFLINE -> return run { session.message.value = AssistantMessage.NeedsConnection }
            else -> return run { session.message.value = AssistantMessage.NeedsSignIn }
        }
        // Undispatched: when the check answers at once (a fake, a cached connection) the orb opens in this call.
        connecting = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!useCases.checkServer()) {
                session.message.value = AssistantMessage.ServerUnreachable
                return@launch
            }
            if (!ui.value.open) startConversation()
        }
    }

    private fun startConversation() {
        conversation.stopAfterword()
        ui.update { it.copy(open = true, mode = OrbMode.Listening, heard = null, saying = null, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
        talking = viewModelScope.launch {
            try {
                conversation.run()
            } finally {
                useCases.listen.cancel()
                session.level.value = 0f
                ui.update { it.copy(open = false, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
            }
        }
    }

    /**
     * The first time this project is opened, the orb opens by itself, introduces the assistant
     * and closes again. It only speaks: nothing is recorded and nothing of the user's is sent
     * (only the fixed introduction, to be voiced). Offline or signed out it waits for a later visit.
     */
    fun introduceIfFirstVisit() {
        // The introduction is about a project's tasks: not on the list of projects.
        val projectId = projectId ?: return
        if (ui.value.open || state.value.availability != AssistantAvailability.READY || useCases.introduction.isDone(projectId)) return
        conversation.stopAfterword()
        introducing = true
        ui.update { it.copy(open = true, mode = OrbMode.Speaking, heard = null, saying = null, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
        talking = viewModelScope.launch {
            try {
                conversation.introduce()
                // Heard to the end: not again for this project.
                useCases.introduction.markDone(projectId)
            } finally {
                introducing = false
                ui.update { it.copy(open = false) }
            }
        }
    }

    /** The first-open introduction is playing; closing it on purpose counts as heard, leaving the app doesn't. */
    private var introducing = false

    /** Stops listening and speaking and closes the orb; nothing is applied. */
    fun close() {
        if (introducing) projectId?.let(useCases.introduction::markDone) // skipped on purpose
        stop()
    }

    private fun stop() {
        connecting?.cancel()
        talking?.cancel()
        useCases.listen.cancel()
        useCases.play.stop()
        ui.update { it.copy(open = false, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
    }

    fun onMicrophoneDenied() {
        session.message.value = AssistantMessage.MicrophoneDenied
    }

    /** The app went to the background: stop listening and talking. */
    fun onLeftApp() {
        // Not a choice to skip the introduction: it plays again on the next visit.
        stop()
        conversation.stopAfterword()
    }

    fun undo() {
        val projectId = projectId ?: return
        if (session.message.value !is AssistantMessage.Applied) return
        val changes = session.lastUndo
        session.message.value = null
        session.lastUndo = emptyList()
        conversation.stopAfterword()
        viewModelScope.launch {
            if (useCases.undo(projectId, changes) is ApplyResult.Rejected) session.message.value = AssistantMessage.UndoNotPossible
        }
    }

    fun dismissMessage() {
        session.message.value = null
    }

    override fun onCleared() {
        useCases.listen.cancel()
        useCases.play.release()
    }

    companion object {
        /** [projectId] comes from the timeline's navigation argument (the shell reads it); null for the list of projects. */
        fun factory(projectId: String?, useCases: () -> AssistantUseCases, phrases: AssistantPhrases): ViewModelProvider.Factory = viewModelFactory {
            initializer { AssistantViewModel(projectId, useCases(), phrases) }
        }
    }
}
