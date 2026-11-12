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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import noel.chronova.app.auth.AuthRepository
import noel.chronova.app.auth.AuthState
import noel.chronova.app.data.remote.ExplainRequest
import noel.chronova.app.data.remote.ExplainStage
import noel.chronova.app.data.remote.VoiceApi
import noel.chronova.app.data.remote.VoiceApiException
import noel.chronova.app.domain.repository.ProjectRepository
import noel.chronova.app.domain.schedule.ApplyResult
import noel.chronova.app.domain.schedule.ApplyScheduleChanges
import noel.chronova.app.domain.schedule.CommandPlanner
import noel.chronova.app.domain.schedule.ScheduleChange
import noel.chronova.app.domain.schedule.ScheduleState
import noel.chronova.app.domain.voice.AICommandHandler
import noel.chronova.app.domain.voice.ExplainedChange
import noel.chronova.app.domain.voice.HandledProposal
import noel.chronova.app.domain.voice.PreviewItem
import noel.chronova.app.domain.voice.VoiceContext
import noel.chronova.app.navigation.ProjectTimelineRoute
import noel.chronova.app.voice.AssistantSettings
import noel.chronova.app.voice.EndpointDetector
import noel.chronova.app.voice.RecorderUnavailable
import noel.chronova.app.voice.SpeechPlayer
import noel.chronova.app.voice.VoiceRecorder
import noel.chronova.app.voice.orb.OrbMode

/** Whether the assistant can be used right now. */
enum class AssistantAvailability {
    /** This build has no server: the assistant isn't offered at all. */
    NOT_CONFIGURED,
    SIGNED_OUT,
    OFFLINE,
    READY,
}

/** The centre orb's state; it only exists while the orb is open. */
data class AssistantUiState(
    val availability: AssistantAvailability = AssistantAvailability.NOT_CONFIGURED,
    val open: Boolean = false,
    val mode: OrbMode = OrbMode.Listening,
    /** Caption: what the assistant heard last. */
    val heard: String? = null,
    /** Caption: what the assistant is saying. */
    val saying: String? = null,
    /** The changes it is asking about, shown under the orb while it waits for yes or no. */
    val proposal: List<PreviewItem> = emptyList(),
    /** The one-time notice (what goes to ElevenLabs and OpenAI) has been accepted. */
    val noticeAccepted: Boolean = false,
)

/** Things to tell the user on the timeline, outside the orb. */
sealed interface AssistantMessage {
    data class Applied(val taskCount: Int, val undo: List<ScheduleChange>) : AssistantMessage
    data object UndoNotPossible : AssistantMessage
    data object NeedsConnection : AssistantMessage
    data object NeedsSignIn : AssistantMessage
    data object MicrophoneDenied : AssistantMessage
}

/**
 * The voice-only assistant. Tapping the orb opens it and it listens; the end of
 * speech is detected ([EndpointDetector]). The server transcribes and interprets;
 * [AICommandHandler] plans against the schedule as it is now. The orb then says
 * what it will change and asks; the spoken answer decides: yes applies (through
 * [ApplyScheduleChanges], refused if the schedule changed since), no closes,
 * anything else is a corrected request. Questions are asked aloud and the answer
 * is folded into the request. After applying, the orb closes first and then the
 * assistant says what changed while the changed tasks are highlighted. Nothing is
 * written without a spoken yes; tapping outside the orb stops everything.
 */
class AssistantViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val voiceApi: VoiceApi,
    private val auth: AuthRepository,
    online: Flow<Boolean>,
    private val recorder: VoiceRecorder,
    private val player: SpeechPlayer,
    private val settings: AssistantSettings,
    private val phrases: AssistantPhrases,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
    newId: () -> String = { UUID.randomUUID().toString() },
    /** A hint for the transcriber: the phone's language ("en", "fil"), or null to let it detect. */
    private val language: () -> String? = { Locale.getDefault().toLanguageTag().substringBefore('-').ifBlank { null } },
) : ViewModel() {

    private val handler = AICommandHandler(CommandPlanner(zone, newId))
    private val applyChanges = ApplyScheduleChanges(repository, zone)
    private val ui = MutableStateFlow(AssistantUiState(noticeAccepted = settings.noticeAccepted))
    private var conversation: Job? = null

    /** Saying what changed, after the orb has closed. */
    private var afterword: Job? = null
    private var greeting: Spoken? = null

    /** The language the user was last heard in, for what the assistant says back. */
    private var heardLanguage: String? = null

    val state: StateFlow<AssistantUiState> = combine(ui, auth.state, online) { ui, auth, online ->
        ui.copy(
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

    private val _level = MutableStateFlow(0f)

    /** The microphone level (0..1) while listening, for the orb's pulse. */
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _highlight = MutableStateFlow<Set<String>>(emptySet())

    /** Tasks just changed by the assistant, highlighted while it says what changed. */
    val highlight: StateFlow<Set<String>> = _highlight.asStateFlow()

    fun acceptNotice() {
        settings.noticeAccepted = true
        ui.update { it.copy(noticeAccepted = true) }
    }

    /** Opens the orb and starts listening (the screen has the microphone permission by now). */
    fun open() {
        if (ui.value.open) return
        when (state.value.availability) {
            AssistantAvailability.READY -> Unit
            AssistantAvailability.OFFLINE -> return run { _message.value = AssistantMessage.NeedsConnection }
            else -> return run { _message.value = AssistantMessage.NeedsSignIn }
        }
        stopAfterword()
        ui.update { it.copy(open = true, mode = OrbMode.Listening, heard = null, saying = null, proposal = emptyList()) }
        conversation = viewModelScope.launch {
            try {
                converse()
            } finally {
                recorder.cancel()
                _level.value = 0f
                ui.update { it.copy(open = false, proposal = emptyList()) }
            }
        }
    }

    /** Stops listening and speaking and closes the orb; nothing is applied. */
    fun close() {
        conversation?.cancel()
        recorder.cancel()
        player.stop()
        ui.update { it.copy(open = false, proposal = emptyList()) }
    }

    fun onMicrophoneDenied() {
        _message.value = AssistantMessage.MicrophoneDenied
    }

    /** The app went to the background: stop listening and talking. */
    fun onLeftApp() {
        close()
        stopAfterword()
    }

    fun undo() {
        val applied = _message.value as? AssistantMessage.Applied ?: return
        _message.value = null
        stopAfterword()
        viewModelScope.launch {
            if (applyChanges(projectId, applied.undo) is ApplyResult.Rejected) _message.value = AssistantMessage.UndoNotPossible
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    private sealed interface Outcome {
        data class Proposal(val items: List<PreviewItem>, val changes: List<ScheduleChange>) : Outcome
        data class Question(val text: String) : Outcome

        /** [final]: nothing more can be done in this conversation. */
        data class Problem(val text: String, val final: Boolean = false) : Outcome
    }

    private suspend fun converse() {
        var context: String? = null
        var pendingWords: String? = null
        var strikes = 0
        try {
            greet()
            while (true) {
                val words = pendingWords ?: listen() ?: run {
                    strikes++
                    if (strikes >= MAX_STRIKES) return say(phrases.goodbye())
                    say(phrases.nothingHeard())
                    null
                } ?: continue
                pendingWords = null
                val request = context?.let { "$it $words" } ?: words
                context = null
                when (val outcome = interpret(request)) {
                    is Outcome.Proposal -> {
                        val reply = propose(outcome) ?: return declined()
                        when (ConfirmationReply.of(reply)) {
                            ConfirmationReply.Yes -> return applyAndExplain(outcome)
                            ConfirmationReply.No -> return declined()
                            ConfirmationReply.Other -> {
                                // A corrected request: worked out again with the first one as context.
                                context = "$request. Change of plan:"
                                pendingWords = reply
                                strikes = 0
                            }
                        }
                    }
                    is Outcome.Question -> {
                        say(outcome.text)
                        context = "$request. Answer to \"${outcome.text}\":"
                    }
                    is Outcome.Problem -> {
                        strikes++
                        say(outcome.text)
                        if (outcome.final || strikes >= MAX_STRIKES) return
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: VoiceApiException) {
            say(phrases.failure(error.kind))
        } catch (_: RecorderUnavailable) {
            say(phrases.microphoneUnavailable())
        }
    }

    /** Listens until the user stops speaking; their words, or null if nothing was heard. */
    private suspend fun listen(): String? {
        ui.update { it.copy(mode = OrbMode.Listening, saying = null) }
        recorder.start()
        val detector = EndpointDetector()
        var elapsed = 0L
        var decision: EndpointDetector.Decision = EndpointDetector.Decision.Continue
        try {
            while (decision == EndpointDetector.Decision.Continue) {
                delay(SAMPLE_MS)
                elapsed += SAMPLE_MS
                val amplitude = recorder.amplitude()
                _level.value = detector.level(amplitude)
                decision = detector.sample(amplitude, elapsed)
            }
        } finally {
            _level.value = 0f
        }
        if (decision == EndpointDetector.Decision.NothingHeard) {
            recorder.cancel()
            return null
        }
        val recording = recorder.stop() ?: return null
        ui.update { it.copy(mode = OrbMode.Thinking) }
        val heard = try {
            val titles = repository.schedule(projectId)?.tasks?.map { it.title }.orEmpty()
            voiceApi.transcribe(token(), recording.file, recording.durationMs, language(), titles)
        } finally {
            recording.file.delete()
        }
        heardLanguage = heard.language ?: language()
        val text = heard.text.trim()
        if (text.isEmpty()) return null
        ui.update { it.copy(heard = text) }
        return text
    }

    private suspend fun interpret(text: String): Outcome {
        ui.update { it.copy(mode = OrbMode.Thinking) }
        val sent = repository.schedule(projectId) ?: return Outcome.Problem(phrases.scheduleChanged(), final = true)
        val context = VoiceContext.from(sent, zone, clock())
        val proposal = voiceApi.interpret(token(), text, context)
        val now = repository.schedule(projectId) ?: return Outcome.Problem(phrases.scheduleChanged(), final = true)
        return when (val handled = handler.handle(context, now, proposal)) {
            is HandledProposal.Preview -> Outcome.Proposal(PreviewItem.from(handled.changes, now), handled.changes)
            is HandledProposal.Clarify -> Outcome.Question(handled.question)
            is HandledProposal.Unsupported -> Outcome.Problem(phrases.unsupported(handled.reason))
            is HandledProposal.CannotDo -> Outcome.Problem(
                phrases.cannotDo(handled.problem, now.tasks.associate { it.id to it.title }, projectEnd(now)),
            )
            HandledProposal.NothingToChange -> Outcome.Problem(phrases.nothingToChange())
            HandledProposal.ScheduleChanged -> Outcome.Problem(phrases.scheduleChanged(), final = true)
            HandledProposal.Unreadable -> Outcome.Problem(phrases.notUnderstood())
        }
    }

    /** Says what will change and asks; the spoken answer, or null if none came. */
    private suspend fun propose(proposal: Outcome.Proposal): String? {
        ui.update { it.copy(proposal = proposal.items) }
        speak(explain(proposal.items, ExplainStage.Proposal))
        return listen()
    }

    private suspend fun applyAndExplain(proposal: Outcome.Proposal) {
        when (val result = applyChanges(projectId, proposal.changes)) {
            is ApplyResult.Rejected -> say(phrases.scheduleChanged())
            is ApplyResult.Applied -> {
                _message.value = AssistantMessage.Applied(taskCount(proposal.changes), result.undo)
                // The orb goes first, so the user sees the timeline while hearing what changed.
                ui.update { it.copy(open = false, proposal = emptyList()) }
                val changed = changedTaskIds(proposal.changes)
                afterword = viewModelScope.launch {
                    _highlight.value = changed
                    try {
                        speak(explain(proposal.items, ExplainStage.Applied), captioned = false)
                    } finally {
                        _highlight.value = emptySet()
                    }
                }
            }
        }
    }

    /** The server's words (and audio) for [items]; null if it couldn't be reached. */
    private suspend fun explain(items: List<PreviewItem>, stage: ExplainStage): Spoken? = try {
        val answer = voiceApi.explain(
            token(),
            ExplainRequest.Changes(
                today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate(),
                changes = ExplainedChange.from(items, zone),
                language = heardLanguage,
                stage = stage,
            ),
        )
        Spoken(answer.text, answer.audio)
    } catch (error: CancellationException) {
        throw error
    } catch (_: VoiceApiException) {
        null
    }

    /** The proposal was turned down (or not answered): its lines go, so nothing looks applied. */
    private suspend fun declined() {
        ui.update { it.copy(proposal = emptyList()) }
        say(phrases.nothingChanged())
    }

    /** Says hello when the orb opens. The voiced greeting is fetched once and replayed after that. */
    private suspend fun greet() {
        val cached = greeting
        if (cached != null) return speak(cached)
        val spoken = voice(phrases.greeting())
        if (spoken.audio != null) greeting = spoken
        speak(spoken)
    }

    /** Says [text] in the assistant's voice (falls back to a caption alone). */
    private suspend fun say(text: String) = speak(voice(text))

    private suspend fun voice(text: String): Spoken = try {
        voiceApi.explain(token(), ExplainRequest.Question(text, heardLanguage)).let { Spoken(it.text, it.audio) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: VoiceApiException) {
        Spoken(text, null)
    }

    private data class Spoken(val text: String, val audio: ByteArray?)

    private suspend fun speak(spoken: Spoken?, captioned: Boolean = true) {
        if (spoken == null) return
        if (captioned) ui.update { it.copy(mode = OrbMode.Speaking, saying = spoken.text) }
        val audio = spoken.audio
        if (audio == null) {
            delay(readingTimeMs(spoken.text))
            return
        }
        player.play(audio)
        player.playing.first { !it }
    }

    private fun stopAfterword() {
        afterword?.cancel()
        afterword = null
        player.stop()
        _highlight.value = emptySet()
    }

    private suspend fun token(): String = auth.accessToken() ?: throw VoiceApiException(VoiceApiException.Kind.UNAUTHORIZED)

    private fun projectEnd(state: ScheduleState): Long = state.project.window(zone).endMillis

    private fun changedTaskIds(changes: List<ScheduleChange>): Set<String> = changes.mapNotNull {
        when (it) {
            is ScheduleChange.UpdateTask -> it.after.id
            is ScheduleChange.CreateTask -> it.task.id
            is ScheduleChange.AddLink -> it.link.successorId
            is ScheduleChange.RemoveLink -> it.link.successorId
            is ScheduleChange.DeleteTask -> null
        }
    }.toSet()

    private fun taskCount(changes: List<ScheduleChange>): Int = changes.mapNotNull {
        when (it) {
            is ScheduleChange.UpdateTask -> it.after.id
            is ScheduleChange.CreateTask -> it.task.id
            is ScheduleChange.DeleteTask -> it.task.id
            is ScheduleChange.AddLink -> it.link.successorId
            is ScheduleChange.RemoveLink -> it.link.successorId
        }
    }.toSet().size

    override fun onCleared() {
        recorder.cancel()
        player.release()
    }

    companion object {
        const val SAMPLE_MS = 100L

        /** Two misses (nothing heard, couldn't do it) in a row end the conversation. */
        const val MAX_STRIKES = 2

        /** How long a caption stays when there's no audio: about 15 characters a second, at least 2 s. */
        fun readingTimeMs(text: String): Long = (text.length * 65L).coerceIn(2_000L, 8_000L)

        fun factory(
            repository: ProjectRepository,
            voiceApi: VoiceApi,
            auth: AuthRepository,
            online: Flow<Boolean>,
            newRecorder: () -> VoiceRecorder,
            newPlayer: () -> SpeechPlayer,
            settings: AssistantSettings,
            phrases: AssistantPhrases,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<ProjectTimelineRoute>()
                AssistantViewModel(route.projectId, repository, voiceApi, auth, online, newRecorder(), newPlayer(), settings, phrases)
            }
        }
    }
}
