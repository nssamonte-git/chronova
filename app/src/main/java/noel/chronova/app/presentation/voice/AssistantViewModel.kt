package noel.chronova.app.presentation.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import noel.chronova.app.auth.AuthRepository
import noel.chronova.app.auth.AuthState
import noel.chronova.app.data.remote.ExplainRequest
import noel.chronova.app.data.remote.VoiceApi
import noel.chronova.app.data.remote.VoiceApiException
import noel.chronova.app.domain.repository.ProjectRepository
import noel.chronova.app.domain.schedule.ApplyResult
import noel.chronova.app.domain.schedule.ApplyScheduleChanges
import noel.chronova.app.domain.schedule.CommandPlanner
import noel.chronova.app.domain.schedule.CommandProblem
import noel.chronova.app.domain.schedule.ScheduleChange
import noel.chronova.app.domain.voice.AICommandHandler
import noel.chronova.app.domain.voice.ExplainedChange
import noel.chronova.app.domain.voice.HandledProposal
import noel.chronova.app.domain.voice.PreviewItem
import noel.chronova.app.domain.voice.VoiceContext
import noel.chronova.app.navigation.ProjectTimelineRoute
import noel.chronova.app.voice.RecorderUnavailable
import noel.chronova.app.voice.Recording
import noel.chronova.app.voice.SpeechPlayer
import noel.chronova.app.voice.SpeechSettings
import noel.chronova.app.voice.VoiceRecorder

/** Whether the assistant can be used right now. */
enum class AssistantAvailability {
    /** This build has no server: the assistant isn't offered at all. */
    NOT_CONFIGURED,
    SIGNED_OUT,
    OFFLINE,
    READY,
}

sealed interface AssistantPhase {
    data object Idle : AssistantPhase

    /** The microphone is on since [startedAtMillis]; Done sends it, Cancel throws it away. */
    data class Listening(val startedAtMillis: Long) : AssistantPhase

    /** The recording is being turned into words; [AssistantViewModel.cancel] stops it. */
    data object Transcribing : AssistantPhase

    /** Waiting for the server; [AssistantViewModel.cancel] stops it. */
    data object Thinking : AssistantPhase

    /** Proposed changes, shown before anything is written. */
    data class Preview(val items: List<PreviewItem>, val changes: List<ScheduleChange>) : AssistantPhase

    data class Question(val text: String) : AssistantPhase

    data class Problem(val problem: AssistantProblem) : AssistantPhase

    data object Applying : AssistantPhase
}

/** While true, the input can't be edited and Send/Speak are hidden. */
val AssistantPhase.busy: Boolean
    get() = this is AssistantPhase.Listening || this is AssistantPhase.Transcribing ||
        this is AssistantPhase.Thinking || this is AssistantPhase.Applying

sealed interface AssistantProblem {
    data object SignedOut : AssistantProblem
    data object Offline : AssistantProblem
    data object Disabled : AssistantProblem
    data object RateLimited : AssistantProblem
    data object Unavailable : AssistantProblem
    data object NotUnderstood : AssistantProblem
    data class Unsupported(val reason: String?) : AssistantProblem
    data object NothingToChange : AssistantProblem

    /** The user said no to the microphone permission; typing still works. */
    data object MicrophoneDenied : AssistantProblem

    /** The microphone couldn't be started (another app may be using it). */
    data object MicrophoneUnavailable : AssistantProblem

    /** The recording was empty or silent. */
    data object NothingHeard : AssistantProblem

    /** More was said than one instruction can hold. */
    data object TooLong : AssistantProblem

    /** The schedule changed while the request was being answered, or before Apply. */
    data object ScheduleChanged : AssistantProblem

    /** [titles] maps the task ids in [problem] to their titles. */
    data class CannotDo(val problem: CommandProblem, val titles: Map<String, String>) : AssistantProblem
}

data class AssistantUiState(
    val availability: AssistantAvailability = AssistantAvailability.NOT_CONFIGURED,
    val open: Boolean = false,
    val input: String = "",
    val phase: AssistantPhase = AssistantPhase.Idle,
    /** The spoken summary of the preview, once it has arrived (spoken commands only). */
    val explanation: String? = null,
    /** An answer was spoken and can be played again. */
    val canReplay: Boolean = false,
    val speaking: Boolean = false,
    /** Spoken answers are off (remembered). */
    val muted: Boolean = false,
)

/** After the assistant applied changes: offers undo. */
sealed interface AssistantMessage {
    data class Applied(val taskCount: Int, val undo: List<ScheduleChange>) : AssistantMessage
    data object UndoNotPossible : AssistantMessage
}

/**
 * The timeline's assistant: the user types or says an instruction (a
 * recording is transcribed by the server first, and the words are shown);
 * the server proposes operations; [AICommandHandler] plans them against the
 * current schedule; the user sees a preview and only Apply writes anything,
 * through the same [ApplyScheduleChanges] path as manual edits.
 */
class AssistantViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val voiceApi: VoiceApi,
    private val auth: AuthRepository,
    online: Flow<Boolean>,
    private val recorder: VoiceRecorder,
    private val player: SpeechPlayer,
    private val speechSettings: SpeechSettings,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
    newId: () -> String = { UUID.randomUUID().toString() },
    /** A hint for the transcriber: the phone's language ("en", "fil"), or null to let it detect. */
    private val language: () -> String? = { Locale.getDefault().toLanguageTag().substringBefore('-').ifBlank { null } },
) : ViewModel() {

    private val handler = AICommandHandler(CommandPlanner(zone, newId))
    private val applyChanges = ApplyScheduleChanges(repository, zone)
    private val ui = MutableStateFlow(AssistantUiState(muted = speechSettings.muted))
    private var request: Job? = null
    private var listeningLimit: Job? = null

    /** Fetching the spoken answer; separate from [request] so the preview never waits for it. */
    private var explaining: Job? = null
    private var answer: ByteArray? = null

    /** The language the last recording was heard in, for the spoken answer. */
    private var heardLanguage: String? = null

    val state: StateFlow<AssistantUiState> = combine(ui, auth.state, online, player.playing) { ui, auth, online, speaking ->
        ui.copy(
            speaking = speaking,
            availability = when {
                auth is AuthState.NotConfigured -> AssistantAvailability.NOT_CONFIGURED
                auth !is AuthState.SignedIn -> AssistantAvailability.SIGNED_OUT
                !online -> AssistantAvailability.OFFLINE
                else -> AssistantAvailability.READY
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssistantUiState())

    private val _message = MutableStateFlow<AssistantMessage?>(null)
    val message: StateFlow<AssistantMessage?> = _message.asStateFlow()

    fun open() = ui.update { it.copy(open = true) }

    /** Closes the sheet; a recording or running request is thrown away and nothing is applied. */
    fun close() {
        stopListening()
        request?.cancel()
        quiet()
        ui.update { it.copy(open = false, phase = AssistantPhase.Idle) }
    }

    fun onInputChange(text: String) {
        // Enter on a hardware keyboard adds a line break: treat it as Send.
        if ('\n' in text) {
            setInput(text.replace('\n', ' ').trim())
            submit()
        } else {
            setInput(text)
        }
    }

    private fun setInput(text: String) {
        if (ui.value.phase.busy) return ui.update { it.copy(input = text) }
        // Editing after an answer starts over; a preview being edited away is dropped.
        quiet()
        ui.update { it.copy(input = text, phase = AssistantPhase.Idle) }
    }

    /** Throws away a recording or stops waiting for the server; nothing is applied. */
    fun cancel() {
        stopListening()
        request?.cancel()
        quiet()
        ui.update { if (it.phase.waiting || it.phase is AssistantPhase.Listening) it.copy(phase = AssistantPhase.Idle) else it }
    }

    fun submit() {
        val text = ui.value.input.trim()
        if (text.isEmpty() || ui.value.phase.busy || !ready()) return
        quiet()
        ui.update { it.copy(phase = AssistantPhase.Thinking) }
        // Typed in: answered on screen only, like other assistants.
        request = viewModelScope.launch { settle(spoken = false) { interpret(text) } }
    }

    /** Turns the microphone on. The screen asks for RECORD_AUDIO first. */
    fun startListening() {
        if (ui.value.phase.busy || !ready()) return
        quiet()
        try {
            recorder.start()
        } catch (_: RecorderUnavailable) {
            return show(AssistantProblem.MicrophoneUnavailable)
        }
        ui.update { it.copy(phase = AssistantPhase.Listening(clock())) }
        listeningLimit = viewModelScope.launch {
            delay(MAX_LISTENING_MS)
            finishListening()
        }
    }

    /** Done speaking: the recording is transcribed, the words shown, then interpreted. */
    fun finishListening() {
        if (ui.value.phase !is AssistantPhase.Listening) return
        listeningLimit?.cancel()
        val recording = recorder.stop() ?: return show(AssistantProblem.NothingHeard)
        ui.update { it.copy(phase = AssistantPhase.Transcribing) }
        request = viewModelScope.launch {
            try {
                settle(spoken = true) { transcribeAndInterpret(recording) }
            } finally {
                recording.file.delete()
            }
        }
    }

    fun onMicrophoneDenied() = show(AssistantProblem.MicrophoneDenied)

    /** The app went to the background: Android silences the microphone there, so a recording is dropped; speech stops. */
    fun onLeftApp() {
        if (ui.value.phase is AssistantPhase.Listening) cancel()
        player.stop()
    }

    fun replay() {
        answer?.takeUnless { ui.value.muted }?.let(player::play)
    }

    /** Turns spoken answers off (stopping one that's playing) or back on; remembered. */
    fun toggleMuted() {
        val muted = !ui.value.muted
        speechSettings.muted = muted
        if (muted) {
            explaining?.cancel()
            player.stop()
        }
        ui.update { it.copy(muted = muted) }
    }

    private fun stopListening() {
        listeningLimit?.cancel()
        if (ui.value.phase is AssistantPhase.Listening) recorder.cancel()
    }

    private fun ready(): Boolean = when (state.value.availability) {
        AssistantAvailability.READY -> true
        AssistantAvailability.OFFLINE -> false.also { show(AssistantProblem.Offline) }
        else -> false.also { show(AssistantProblem.SignedOut) }
    }

    /**
     * Runs a server round trip and shows its outcome, unless it was cancelled
     * meanwhile. A [spoken] command then gets a spoken answer, fetched apart.
     */
    private suspend fun settle(spoken: Boolean, work: suspend () -> AssistantPhase) {
        val phase = try {
            work()
        } catch (error: CancellationException) {
            throw error
        } catch (error: VoiceApiException) {
            AssistantPhase.Problem(problemFor(error.kind))
        }
        var shown = false
        ui.update { if (it.phase.waiting) it.copy(phase = phase).also { shown = true } else it }
        if (shown && spoken) explain(phase)
    }

    /** Fetches and plays what [phase] is about; nothing happens if it fails, is muted, or the phase moved on. */
    private fun explain(phase: AssistantPhase) {
        val request = when (phase) {
            is AssistantPhase.Preview -> ExplainRequest.Changes(
                today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate(),
                changes = ExplainedChange.from(phase.items, zone),
                language = heardLanguage,
            )
            is AssistantPhase.Question -> ExplainRequest.Question(phase.text, heardLanguage)
            else -> return
        }
        if (ui.value.muted) return
        explaining = viewModelScope.launch {
            val token = auth.accessToken() ?: return@launch
            val spoken = try {
                voiceApi.explain(token, request)
            } catch (error: CancellationException) {
                throw error
            } catch (_: VoiceApiException) {
                return@launch // The preview already says it all.
            }
            if (ui.value.phase !== phase || ui.value.muted) return@launch
            answer = spoken.audio
            ui.update {
                it.copy(explanation = spoken.text.takeIf { phase is AssistantPhase.Preview }, canReplay = spoken.audio != null)
            }
            spoken.audio?.let(player::play)
        }
    }

    /** Stops and forgets any spoken answer. */
    private fun quiet() {
        explaining?.cancel()
        player.stop()
        answer = null
        ui.update { it.copy(explanation = null, canReplay = false) }
    }

    private suspend fun transcribeAndInterpret(recording: Recording): AssistantPhase {
        val token = auth.accessToken() ?: return AssistantPhase.Problem(AssistantProblem.SignedOut)
        // The project's own task names, so they're heard right ("Build", not "filled").
        val titles = repository.schedule(projectId)?.tasks?.map { it.title }.orEmpty()
        val heard = voiceApi.transcribe(token, recording.file, recording.durationMs, language(), titles)
        val text = heard.text
        heardLanguage = heard.language ?: language()
        if (text.isEmpty()) return AssistantPhase.Problem(AssistantProblem.NothingHeard)
        // Show what was heard while it's being worked out; the user can correct it and Send again.
        ui.update { if (it.phase is AssistantPhase.Transcribing) it.copy(input = text, phase = AssistantPhase.Thinking) else it }
        return interpret(text)
    }

    private suspend fun interpret(text: String): AssistantPhase {
        val token = auth.accessToken() ?: return AssistantPhase.Problem(AssistantProblem.SignedOut)
        val sent = repository.schedule(projectId) ?: return AssistantPhase.Problem(AssistantProblem.ScheduleChanged)
        val context = VoiceContext.from(sent, zone, clock())
        val proposal = voiceApi.interpret(token, text, context)
        val now = repository.schedule(projectId) ?: return AssistantPhase.Problem(AssistantProblem.ScheduleChanged)
        return when (val handled = handler.handle(context, now, proposal)) {
            is HandledProposal.Preview -> AssistantPhase.Preview(PreviewItem.from(handled.changes, now), handled.changes)
            is HandledProposal.Clarify -> AssistantPhase.Question(handled.question)
            is HandledProposal.Unsupported -> AssistantPhase.Problem(AssistantProblem.Unsupported(handled.reason))
            is HandledProposal.CannotDo ->
                AssistantPhase.Problem(AssistantProblem.CannotDo(handled.problem, now.tasks.associate { it.id to it.title }))
            HandledProposal.NothingToChange -> AssistantPhase.Problem(AssistantProblem.NothingToChange)
            HandledProposal.ScheduleChanged -> AssistantPhase.Problem(AssistantProblem.ScheduleChanged)
            HandledProposal.Unreadable -> AssistantPhase.Problem(AssistantProblem.NotUnderstood)
        }
    }

    /** Writes the previewed changes; refused (nothing written) if the schedule changed since the preview. */
    fun apply() {
        val preview = ui.value.phase as? AssistantPhase.Preview ?: return
        quiet()
        ui.update { it.copy(phase = AssistantPhase.Applying) }
        viewModelScope.launch {
            when (val result = applyChanges(projectId, preview.changes)) {
                is ApplyResult.Applied -> {
                    ui.update { AssistantUiState(availability = it.availability, muted = it.muted) }
                    _message.value = AssistantMessage.Applied(taskCount(preview.changes), result.undo)
                }
                is ApplyResult.Rejected -> show(AssistantProblem.ScheduleChanged)
            }
        }
    }

    fun undo() {
        val applied = _message.value as? AssistantMessage.Applied ?: return
        _message.value = null
        viewModelScope.launch {
            if (applyChanges(projectId, applied.undo) is ApplyResult.Rejected) _message.value = AssistantMessage.UndoNotPossible
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    private fun show(problem: AssistantProblem) = ui.update { it.copy(phase = AssistantPhase.Problem(problem)) }

    private val AssistantPhase.waiting get() = this is AssistantPhase.Transcribing || this is AssistantPhase.Thinking

    override fun onCleared() {
        stopListening()
        player.release()
    }

    private fun problemFor(kind: VoiceApiException.Kind): AssistantProblem = when (kind) {
        VoiceApiException.Kind.UNAUTHORIZED -> AssistantProblem.SignedOut
        VoiceApiException.Kind.DISABLED -> AssistantProblem.Disabled
        VoiceApiException.Kind.RATE_LIMITED -> AssistantProblem.RateLimited
        VoiceApiException.Kind.NETWORK -> AssistantProblem.Offline
        VoiceApiException.Kind.TOO_LONG -> AssistantProblem.TooLong
        VoiceApiException.Kind.UNAVAILABLE, VoiceApiException.Kind.REJECTED -> AssistantProblem.Unavailable
    }

    private fun taskCount(changes: List<ScheduleChange>): Int = changes.mapNotNull {
        when (it) {
            is ScheduleChange.UpdateTask -> it.after.id
            is ScheduleChange.CreateTask -> it.task.id
            is ScheduleChange.DeleteTask -> it.task.id
            is ScheduleChange.AddLink -> it.link.successorId
            is ScheduleChange.RemoveLink -> it.link.successorId
        }
    }.toSet().size

    companion object {
        /** Matches the server's limit (`docs/voice-api.md`). */
        const val MAX_LISTENING_MS = 60_000L

        fun factory(
            repository: ProjectRepository,
            voiceApi: VoiceApi,
            auth: AuthRepository,
            online: Flow<Boolean>,
            newRecorder: () -> VoiceRecorder,
            newPlayer: () -> SpeechPlayer,
            speechSettings: SpeechSettings,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<ProjectTimelineRoute>()
                AssistantViewModel(route.projectId, repository, voiceApi, auth, online, newRecorder(), newPlayer(), speechSettings)
            }
        }
    }
}
