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
import noel.chronova.app.domain.auth.AuthRepository
import noel.chronova.app.domain.auth.AuthState
import noel.chronova.app.data.remote.DecisionAnswer
import noel.chronova.app.data.remote.ExplainRequest
import noel.chronova.app.data.remote.ExplainStage
import noel.chronova.app.data.remote.VoiceApi
import noel.chronova.app.data.remote.VoiceApiException
import noel.chronova.app.data.remote.VoiceDecision
import noel.chronova.app.domain.notifications.NotificationKind
import noel.chronova.app.domain.notifications.NotificationRepository
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
import noel.chronova.app.domain.voice.Proposal
import noel.chronova.app.domain.voice.ProposedOperation
import noel.chronova.app.domain.voice.ScheduleWarning
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
    /** Clashes the proposal would cause, shown under its lines; the assistant names them before asking. */
    val warnings: List<ScheduleWarning> = emptyList(),
    /** A clash-free alternative the assistant is offering instead, shown under the warnings. */
    val offer: List<PreviewItem> = emptyList(),
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
    /** Where applied changes are recorded (with their undo) for the notifications page. */
    private val notifications: NotificationRepository? = null,
) : ViewModel() {

    private val handler = AICommandHandler(CommandPlanner(zone, newId))
    private val applyChanges = ApplyScheduleChanges(repository, zone)
    private val ui = MutableStateFlow(AssistantUiState(noticeAccepted = settings.noticeAccepted))
    private var conversation: Job? = null

    /** Saying what changed, after the orb has closed. */
    private var afterword: Job? = null
    private var greeting: Spoken? = null

    /** How loud the last words heard were ([EndpointDetector.loudestSpeech]). */
    private var lastLoudness = 0

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

    private val explaining = MutableStateFlow(false)

    /**
     * The assistant is at work: the orb is open (listening, thinking, talking, applying) or, once it
     * has closed, it is still saying what it changed. The screen keeps its orientation meanwhile.
     */
    val busy: StateFlow<Boolean> = combine(ui, explaining) { ui, explaining -> ui.open || explaining }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
        ui.update { it.copy(open = true, mode = OrbMode.Listening, heard = null, saying = null, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
        conversation = viewModelScope.launch {
            try {
                converse()
            } finally {
                recorder.cancel()
                _level.value = 0f
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
        if (ui.value.open || state.value.availability != AssistantAvailability.READY || settings.introduced(projectId)) return
        stopAfterword()
        introducing = true
        ui.update { it.copy(open = true, mode = OrbMode.Speaking, heard = null, saying = null, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
        conversation = viewModelScope.launch {
            try {
                say(phrases.introduction())
                // Heard to the end: not again for this project.
                settings.markIntroduced(projectId)
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
        if (introducing) settings.markIntroduced(projectId) // skipped on purpose
        stop()
    }

    private fun stop() {
        conversation?.cancel()
        recorder.cancel()
        player.stop()
        ui.update { it.copy(open = false, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
    }

    fun onMicrophoneDenied() {
        _message.value = AssistantMessage.MicrophoneDenied
    }

    /** The app went to the background: stop listening and talking. */
    fun onLeftApp() {
        // Not a choice to skip the introduction: it plays again on the next visit.
        stop()
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
        data class Proposal(
            val items: List<PreviewItem>,
            val changes: List<ScheduleChange>,
            val warnings: List<ScheduleWarning> = emptyList(),
            /** A clash-free version to offer instead, when [warnings] has clashes and one was found. */
            val alternative: Proposal? = null,
        ) : Outcome
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
                        val alternative = outcome.alternative
                        val heard = propose(outcome) ?: return declined()
                        val (kind, reply) = answer(heard) ?: return declined()
                        val decided = when (kind) {
                            ConfirmationReply.Yes -> if (alternative != null) {
                                report(request, outcome, DecisionAnswer.AcceptedFix)
                                return applyAndExplain(alternative)
                            } else {
                                report(request, outcome, DecisionAnswer.Accepted)
                                return applyAndExplain(outcome)
                            }
                            ConfirmationReply.No -> if (alternative == null) {
                                report(request, outcome, DecisionAnswer.Declined)
                                return declined()
                            } else {
                                // They turned the alternative down: their own version is still open.
                                keepAsAsked(request, outcome) ?: return
                            }
                            ConfirmationReply.Other -> reply
                        }
                        // A corrected request: worked out again with the first one as context.
                        report(request, outcome, DecisionAnswer.Corrected, reply = decided)
                        context = "$request. Change of plan:"
                        pendingWords = decided
                        strikes = 0
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
            // The server ended the session: the app goes back to the sign-in page.
            if (error.kind == VoiceApiException.Kind.UNAUTHORIZED) auth.endSession()
        } catch (_: RecorderUnavailable) {
            say(phrases.microphoneUnavailable())
        }
    }

    /**
     * Listens until the user stops speaking; their words, or null if nothing was heard.
     * [answering]: a yes or no is expected, so the transcriber listens for those words too,
     * and a sound that turns out to hold no words (a cough, a door) gets one more listen
     * instead of counting as no answer.
     */
    private suspend fun listen(answering: Boolean = false): String? {
        repeat(if (answering) 2 else 1) {
            when (val heard = hear(answering)) {
                Heard.Silence -> return null
                Heard.NoWords -> Unit
                is Heard.Words -> return heard.text
            }
        }
        return null
    }

    private sealed interface Heard {
        /** Nobody spoke before the no-speech limit. */
        data object Silence : Heard

        /** Something was loud enough to record but held no words. */
        data object NoWords : Heard
        data class Words(val text: String) : Heard
    }

    private suspend fun hear(answering: Boolean): Heard {
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
            return Heard.Silence
        }
        val recording = recorder.stop() ?: return Heard.Silence
        ui.update { it.copy(mode = OrbMode.Thinking) }
        val heard = try {
            val titles = repository.schedule(projectId)?.tasks?.map { it.title }.orEmpty()
            val keyterms = if (answering) ConfirmationReply.KEYTERMS + titles else titles
            voiceApi.transcribe(token(), recording.file, recording.durationMs, language(), keyterms)
        } finally {
            recording.file.delete()
        }
        heardLanguage = heard.language ?: language()
        val text = heard.text.trim()
        if (text.isEmpty()) return Heard.NoWords
        lastLoudness = detector.loudestSpeech
        ui.update { it.copy(heard = text) }
        return Heard.Words(text)
    }

    private suspend fun interpret(text: String): Outcome {
        ui.update { it.copy(mode = OrbMode.Thinking) }
        val sent = repository.schedule(projectId) ?: return Outcome.Problem(phrases.scheduleChanged(), final = true)
        val context = VoiceContext.from(sent, zone, clock())
        val proposal = voiceApi.interpret(token(), text, context)
        val now = repository.schedule(projectId) ?: return Outcome.Problem(phrases.scheduleChanged(), final = true)
        return when (val handled = handler.handle(context, now, proposal)) {
            is HandledProposal.Preview -> {
                val planned = Outcome.Proposal(PreviewItem.from(handled.changes, now), handled.changes, ScheduleWarning.from(handled.changes, now))
                if (planned.warnings.isEmpty()) planned else planned.copy(alternative = alternative(text, context, now, proposal, planned))
            }
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

    /**
     * A clash-free version of [planned]: the model's, if it is valid and clash-free
     * here (the one that came with the proposal, else one asked for now), else the
     * app's own (push what waits for a task just enough), if that is; null when there is none.
     */
    private suspend fun alternative(
        text: String,
        context: VoiceContext,
        state: ScheduleState,
        proposal: Proposal,
        planned: Outcome.Proposal,
    ): Outcome.Proposal? {
        fun usable(candidate: Proposal): Outcome.Proposal? {
            val handled = handler.handle(context, state, candidate) as? HandledProposal.Preview ?: return null
            if (ScheduleWarning.from(handled.changes, state).isNotEmpty()) return null
            val items = PreviewItem.from(handled.changes, state)
            // The same change in other words is no alternative.
            if (ExplainedChange.from(items, zone) == ExplainedChange.from(planned.items, zone)) return null
            return Outcome.Proposal(items, handled.changes)
        }
        // Usually the interpreter saw the clash and answered with an alternative already: no extra round trip.
        proposal.alternative?.takeIf { it.isNotEmpty() }
            ?.let { usable(proposal.copy(operations = it, alternative = null)) }
            ?.let { return it }
        val suggested = try {
            voiceApi.resolve(token(), text, context, proposal.operations, planned.warnings)
        } catch (error: CancellationException) {
            throw error
        } catch (_: VoiceApiException) {
            null
        }
        suggested?.takeIf { it.result == "operations" }?.let(::usable)?.let { return it }
        val waitedFor = ScheduleWarning.waitedFor(planned.changes, state)
        if (waitedFor.isEmpty()) return null
        return usable(proposal.copy(operations = proposal.operations + waitedFor.map { ProposedOperation("reschedule_dependents", taskId = it) }))
    }

    /** Says what will change and asks; the spoken answer, or null if none came. */
    private suspend fun propose(proposal: Outcome.Proposal): String? {
        val offer = proposal.alternative?.items.orEmpty()
        // On screen, only what the alternative does differently (it says the same).
        val asked = ExplainedChange.from(proposal.items, zone)
        val different = offer.filter { item -> ExplainedChange.from(listOf(item), zone).any { it !in asked } }
        ui.update { it.copy(proposal = proposal.items, warnings = proposal.warnings, offer = different) }
        // A clash is named before the question, which offers the alternative or asks whether to apply it anyway.
        speak(explain(proposal.items, ExplainStage.Proposal, proposal.warnings, offer))
        return listen(answering = true)
    }

    /**
     * After the alternative was turned down: asks whether to apply [proposal] as
     * asked. Applies it on yes; null when the conversation is over, or the words
     * of a corrected request.
     */
    /**
     * Reads an answer to a yes/no question. A yes quieter than someone talking to the phone
     * ([NEAR_SPEECH_LEVEL]) may be a voice across the room (seen on the phone: a "yes" nobody
     * there meant applied a change), so it is asked about once more; null when there is
     * still no clear answer.
     */
    private suspend fun answer(heard: String): Pair<ConfirmationReply, String>? {
        val kind = ConfirmationReply.of(heard)
        if (kind != ConfirmationReply.Yes || lastLoudness >= NEAR_SPEECH_LEVEL) return kind to heard
        say(phrases.wasThatAYes())
        val again = listen(answering = true) ?: return null
        val second = ConfirmationReply.of(again)
        if (second == ConfirmationReply.Yes && lastLoudness < NEAR_SPEECH_LEVEL) return null
        return second to again
    }

    private suspend fun keepAsAsked(request: String, proposal: Outcome.Proposal): String? {
        ui.update { it.copy(offer = emptyList()) }
        say(phrases.applyAsAsked())
        val heard = listen(answering = true)
        val (kind, reply) = heard?.let { answer(it) } ?: return run {
            report(request, proposal, DecisionAnswer.Declined)
            declined()
            null
        }
        return when (kind) {
            ConfirmationReply.Yes -> {
                report(request, proposal, DecisionAnswer.KeptAsIs)
                applyAndExplain(proposal)
                null
            }
            ConfirmationReply.No -> {
                report(request, proposal, DecisionAnswer.Declined)
                declined()
                null
            }
            ConfirmationReply.Other -> reply
        }
    }

    /** Tells the server how the user answered (best effort: nothing waits for it, failures are ignored). */
    private fun report(request: String, proposal: Outcome.Proposal, answer: DecisionAnswer, reply: String? = null) {
        val proposed = ExplainedChange.from(proposal.items, zone)
        if (proposed.isEmpty()) return
        val offered = proposal.alternative?.let { ExplainedChange.from(it.items, zone) }.orEmpty()
        viewModelScope.launch {
            try {
                voiceApi.decide(token(), VoiceDecision(request, proposed, answer, offered, reply))
            } catch (error: CancellationException) {
                throw error
            } catch (_: VoiceApiException) {
                // Learning is a nicety; the conversation never depends on it.
            }
        }
    }

    private suspend fun applyAndExplain(proposal: Outcome.Proposal) {
        when (val result = applyChanges(projectId, proposal.changes)) {
            is ApplyResult.Rejected -> say(phrases.scheduleChanged())
            is ApplyResult.Applied -> {
                _message.value = AssistantMessage.Applied(taskCount(proposal.changes), result.undo)
                record(proposal, result.undo)
                // Still busy while the orb gives way to the explanation: no gap in between.
                explaining.value = true
                // The orb goes first, so the user sees the timeline while hearing what changed.
                ui.update { it.copy(open = false, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
                val changed = changedTaskIds(proposal.changes)
                afterword = viewModelScope.launch {
                    _highlight.value = changed
                    try {
                        speak(explain(proposal.items, ExplainStage.Applied), captioned = false)
                    } finally {
                        _highlight.value = emptySet()
                        explaining.value = false
                    }
                }
            }
        }
    }

    /** Adds the change to the notifications page, with what undoes it. */
    private suspend fun record(proposal: Outcome.Proposal, undo: List<ScheduleChange>) {
        val notifications = notifications ?: return
        val project = repository.schedule(projectId)?.project
        val titles = ExplainedChange.from(proposal.items, zone).mapNotNull { it.task ?: it.successor }.distinct()
        val args = mutableMapOf("count" to "${taskCount(proposal.changes)}", "tasks" to titles.joinToString("\n"))
        val clash = proposal.warnings.firstOrNull()
        when (clash) {
            is ScheduleWarning.Overlap -> args += mapOf("clash" to "overlap", "first" to clash.task, "second" to clash.other)
            is ScheduleWarning.BrokenLink -> args += mapOf("clash" to "link", "first" to clash.successor, "second" to clash.predecessor)
            null -> Unit
        }
        val kind = if (clash == null) NotificationKind.ASSISTANT_APPLIED else NotificationKind.ASSISTANT_CLASH
        notifications.add(kind, args, projectId, project?.name, undo)
    }

    /** The server's words (and audio) for [items]; null if it couldn't be reached. */
    private suspend fun explain(
        items: List<PreviewItem>,
        stage: ExplainStage,
        warnings: List<ScheduleWarning> = emptyList(),
        offer: List<PreviewItem> = emptyList(),
    ): Spoken? = try {
        val answer = voiceApi.explain(
            token(),
            ExplainRequest.Changes(
                today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate(),
                changes = ExplainedChange.from(items, zone),
                language = heardLanguage,
                stage = stage,
                warnings = warnings,
                offer = ExplainedChange.from(offer, zone),
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
        ui.update { it.copy(proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
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
        explaining.value = false
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

        /**
         * How loud (the [EndpointDetector.loudestSpeech] of the answer) a "yes" must be to count at
         * once. Measured on the test phone (Android 10): requests said to the phone 8,900–10,200, a
         * one-word "yes" ~4,700, a "yes" at the volume of a voice across the room 1,400–1,600.
         */
        const val NEAR_SPEECH_LEVEL = 3_000

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
            notifications: NotificationRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<ProjectTimelineRoute>()
                AssistantViewModel(
                    route.projectId, repository, voiceApi, auth, online, newRecorder(), newPlayer(), settings, phrases,
                    notifications = notifications,
                )
            }
        }
    }
}
