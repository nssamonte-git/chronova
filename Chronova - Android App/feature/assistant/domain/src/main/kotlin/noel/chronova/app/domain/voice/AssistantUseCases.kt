package noel.chronova.app.domain.voice

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import noel.chronova.app.domain.auth.AuthRepository
import noel.chronova.app.domain.auth.AuthState
import noel.chronova.app.domain.notifications.NotificationKind
import noel.chronova.app.domain.notifications.NotificationRepository
import noel.chronova.app.domain.repository.ProjectRepository
import noel.chronova.app.domain.schedule.ApplyResult
import noel.chronova.app.domain.schedule.ApplyScheduleChanges
import noel.chronova.app.domain.schedule.CommandPlanner
import noel.chronova.app.domain.schedule.CommandProblem
import noel.chronova.app.domain.schedule.ScheduleChange
import noel.chronova.app.domain.schedule.ScheduleState

/** Whether the assistant can be used right now. */
enum class VoiceAvailability {
    /** This build has no server: the assistant isn't offered at all. */
    NOT_CONFIGURED,
    SIGNED_OUT,
    OFFLINE,
    READY,
}

class ObserveVoiceAvailability(private val auth: AuthRepository, private val online: Flow<Boolean>) {
    operator fun invoke(): Flow<VoiceAvailability> = combine(auth.state, online) { auth, online ->
        when {
            auth is AuthState.NotConfigured -> VoiceAvailability.NOT_CONFIGURED
            auth !is AuthState.SignedIn -> VoiceAvailability.SIGNED_OUT
            !online -> VoiceAvailability.OFFLINE
            else -> VoiceAvailability.READY
        }
    }
}

/** Whether the server can be reached right now; any failure counts as no. */
class CheckVoiceServer(private val voiceApi: VoiceApi) {
    suspend operator fun invoke(): Boolean = try {
        voiceApi.reachable()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
}

/** The voice endpoints' access token; no session counts as the server refusing it. */
class VoiceAccessToken(private val auth: AuthRepository) {
    suspend operator fun invoke(): String = auth.accessToken() ?: throw VoiceApiException(VoiceApiException.Kind.UNAUTHORIZED)
}

/** The server ended the session (a refused token): the app goes back to the sign-in page. */
class EndVoiceSession(private val auth: AuthRepository) {
    suspend operator fun invoke() = auth.endSession()
}

/** The one-time notice about what goes to ElevenLabs and OpenAI. */
class VoiceNotice(private val settings: AssistantSettings) {
    val accepted: Boolean get() = settings.noticeAccepted

    fun accept() {
        settings.noticeAccepted = true
    }
}

/** Whether the assistant has introduced itself in a project (it does so on the first open). */
class ProjectIntroduction(private val settings: AssistantSettings) {
    fun isDone(projectId: String): Boolean = settings.introduced(projectId)
    fun markDone(projectId: String) = settings.markIntroduced(projectId)
}

/** What listening produced. */
sealed interface Listened {
    /** Nobody spoke before the no-speech limit (or nothing usable was recorded). */
    data object NothingHeard : Listened

    /** A recording, and how loud its loudest speech was ([EndpointDetector.loudestSpeech]). */
    data class Recorded(val recording: Recording, val loudestSpeech: Int) : Listened
}

/**
 * Records from the microphone until the speaker stops ([EndpointDetector]), reporting the
 * level (0..1) for the orb's pulse as it goes. @throws RecorderUnavailable if the microphone can't be used.
 */
class ListenForSpeech(private val recorder: VoiceRecorder, private val sampleMs: Long = SAMPLE_MS) {
    suspend operator fun invoke(onLevel: (Float) -> Unit): Listened {
        recorder.start()
        val detector = EndpointDetector()
        var elapsed = 0L
        var decision: EndpointDetector.Decision = EndpointDetector.Decision.Continue
        try {
            while (decision == EndpointDetector.Decision.Continue) {
                delay(sampleMs)
                elapsed += sampleMs
                val amplitude = recorder.amplitude()
                onLevel(detector.level(amplitude))
                decision = detector.sample(amplitude, elapsed)
            }
        } finally {
            onLevel(0f)
        }
        if (decision == EndpointDetector.Decision.NothingHeard) {
            recorder.cancel()
            return Listened.NothingHeard
        }
        val recording = recorder.stop() ?: return Listened.NothingHeard
        return Listened.Recorded(recording, detector.loudestSpeech)
    }

    /** Stops and throws away any recording in progress. */
    fun cancel() = recorder.cancel()

    companion object {
        const val SAMPLE_MS = 100L
    }
}

/** Plays the assistant's spoken answers, one at a time. */
class PlaySpeech(private val player: SpeechPlayer) {
    /** Plays [audio] and returns when it has finished (or was stopped). */
    suspend operator fun invoke(audio: ByteArray) {
        player.play(audio)
        player.playing.first { !it }
    }

    fun stop() = player.stop()
    fun release() = player.release()
}

/**
 * Sends a recording to be transcribed, with the project's task titles (and, when a yes or
 * no is expected, those words) as terms to listen for. The recording is deleted either way.
 */
class TranscribeSpeech(
    private val repository: ProjectRepository,
    private val voiceApi: VoiceApi,
    private val token: VoiceAccessToken,
) {
    suspend operator fun invoke(projectId: String, recording: Recording, language: String?, answering: Boolean): Transcript {
        try {
            val titles = repository.schedule(projectId)?.tasks?.map { it.title }.orEmpty()
            val keyterms = if (answering) ConfirmationReply.KEYTERMS + titles else titles
            return voiceApi.transcribe(token(), recording.file, recording.durationMs, language, keyterms)
        } finally {
            recording.file.delete()
        }
    }
}

/** Changes the assistant will ask about: their lines, the changes, the clashes they would cause, and a clash-free alternative. */
data class VoicePlan(
    val items: List<PreviewItem>,
    val changes: List<ScheduleChange>,
    val warnings: List<ScheduleWarning> = emptyList(),
    /** A clash-free version to offer instead, when [warnings] has clashes and one was found. */
    val alternative: VoicePlan? = null,
)

/** What a request turned into. */
sealed interface Interpretation {
    data class Planned(val plan: VoicePlan) : Interpretation
    data class Question(val text: String) : Interpretation
    data class Unsupported(val reason: String?) : Interpretation
    data class CannotDo(val problem: CommandProblem, val titles: Map<String, String>, val projectEndMillis: Long) : Interpretation
    data object NothingToChange : Interpretation

    /** The schedule changed (or the project went) meanwhile: nothing more can be done in this conversation. */
    data object ScheduleChanged : Interpretation
    data object Unreadable : Interpretation
}

/**
 * The server interprets the words; the app plans them against the schedule as it is now
 * ([AICommandHandler]). A plan that would cause clashes gets a clash-free alternative when
 * one exists: the model's, if it is valid and clash-free here (the one that came with the
 * proposal, else one asked for now), else the app's own (push what waits for a task just enough).
 */
class InterpretRequest(
    private val repository: ProjectRepository,
    private val voiceApi: VoiceApi,
    private val token: VoiceAccessToken,
    private val handler: AICommandHandler,
    private val zone: ZoneId,
    private val clock: () -> Long,
) {
    suspend operator fun invoke(projectId: String, text: String): Interpretation {
        val sent = repository.schedule(projectId) ?: return Interpretation.ScheduleChanged
        val context = VoiceContext.from(sent, zone, clock())
        val proposal = voiceApi.interpret(token(), text, context)
        val now = repository.schedule(projectId) ?: return Interpretation.ScheduleChanged
        return when (val handled = handler.handle(context, now, proposal)) {
            is HandledProposal.Preview -> {
                val planned = VoicePlan(PreviewItem.from(handled.changes, now), handled.changes, ScheduleWarning.from(handled.changes, now))
                Interpretation.Planned(
                    if (planned.warnings.isEmpty()) planned else planned.copy(alternative = alternative(text, context, now, proposal, planned)),
                )
            }
            is HandledProposal.Clarify -> Interpretation.Question(handled.question)
            is HandledProposal.Unsupported -> Interpretation.Unsupported(handled.reason)
            is HandledProposal.CannotDo -> Interpretation.CannotDo(
                handled.problem,
                now.tasks.associate { it.id to it.title },
                now.project.window(zone).endMillis,
            )
            HandledProposal.NothingToChange -> Interpretation.NothingToChange
            HandledProposal.ScheduleChanged -> Interpretation.ScheduleChanged
            HandledProposal.Unreadable -> Interpretation.Unreadable
        }
    }

    private suspend fun alternative(
        text: String,
        context: VoiceContext,
        state: ScheduleState,
        proposal: Proposal,
        planned: VoicePlan,
    ): VoicePlan? {
        fun usable(candidate: Proposal): VoicePlan? {
            val handled = handler.handle(context, state, candidate) as? HandledProposal.Preview ?: return null
            if (ScheduleWarning.from(handled.changes, state).isNotEmpty()) return null
            val items = PreviewItem.from(handled.changes, state)
            // The same change in other words is no alternative.
            if (ExplainedChange.from(items, zone) == ExplainedChange.from(planned.items, zone)) return null
            return VoicePlan(items, handled.changes)
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

    companion object {
        /** Wires the planner (new task ids from [newId]) for the app and tests. */
        fun create(repository: ProjectRepository, voiceApi: VoiceApi, token: VoiceAccessToken, zone: ZoneId, clock: () -> Long, newId: () -> String) =
            InterpretRequest(repository, voiceApi, token, AICommandHandler(CommandPlanner(zone, newId)), zone, clock)
    }
}

/** The server's words (and audio) for changes, as a proposal or as applied; null if it couldn't be reached. */
class ExplainChanges(
    private val voiceApi: VoiceApi,
    private val token: VoiceAccessToken,
    private val zone: ZoneId,
    private val clock: () -> Long,
) {
    suspend operator fun invoke(
        items: List<PreviewItem>,
        stage: ExplainStage,
        language: String?,
        warnings: List<ScheduleWarning> = emptyList(),
        offer: List<PreviewItem> = emptyList(),
    ): SpokenAnswer? = try {
        voiceApi.explain(
            token(),
            ExplainRequest.Changes(
                today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate(),
                changes = ExplainedChange.from(items, zone),
                language = language,
                stage = stage,
                warnings = warnings,
                offer = ExplainedChange.from(offer, zone),
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: VoiceApiException) {
        null
    }
}

/** [text] in the assistant's voice; the words alone (no audio) if the server couldn't voice them. */
class VoicePhrase(private val voiceApi: VoiceApi, private val token: VoiceAccessToken) {
    suspend operator fun invoke(text: String, language: String?): SpokenAnswer = try {
        voiceApi.explain(token(), ExplainRequest.Question(text, language))
    } catch (error: CancellationException) {
        throw error
    } catch (_: VoiceApiException) {
        SpokenAnswer(text, null, null)
    }
}

/** Tells the server how the user answered (best effort: failures are ignored). */
class RecordDecision(private val voiceApi: VoiceApi, private val token: VoiceAccessToken, private val zone: ZoneId) {
    suspend operator fun invoke(request: String, plan: VoicePlan, answer: DecisionAnswer, reply: String? = null) {
        val proposed = ExplainedChange.from(plan.items, zone)
        if (proposed.isEmpty()) return
        val offered = plan.alternative?.let { ExplainedChange.from(it.items, zone) }.orEmpty()
        try {
            voiceApi.decide(token(), VoiceDecision(request, proposed, answer, offered, reply))
        } catch (error: CancellationException) {
            throw error
        } catch (_: VoiceApiException) {
            // Learning is a nicety; the conversation never depends on it.
        }
    }
}

/** Applies a spoken-yes change set through the one apply path; refused if the schedule changed since. */
class ApplyVoicePlan(private val applyChanges: ApplyScheduleChanges) {
    suspend operator fun invoke(projectId: String, plan: VoicePlan): ApplyResult = applyChanges(projectId, plan.changes)
}

/** Reverses an applied change set, unless those tasks were edited again since. */
class UndoVoiceChange(private val applyChanges: ApplyScheduleChanges) {
    suspend operator fun invoke(projectId: String, undo: List<ScheduleChange>): ApplyResult = applyChanges(projectId, undo)
}

/** Adds an applied change to the notifications page, with what undoes it. */
class RecordVoiceChange(
    private val notifications: NotificationRepository?,
    private val repository: ProjectRepository,
    private val zone: ZoneId,
) {
    suspend operator fun invoke(projectId: String, plan: VoicePlan, undo: List<ScheduleChange>) {
        val notifications = notifications ?: return
        val project = repository.schedule(projectId)?.project
        val titles = ExplainedChange.from(plan.items, zone).mapNotNull { it.task ?: it.successor }.distinct()
        val args = mutableMapOf("count" to "${plan.changes.taskCount()}", "tasks" to titles.joinToString("\n"))
        val clash = plan.warnings.firstOrNull()
        when (clash) {
            is ScheduleWarning.Overlap -> args += mapOf("clash" to "overlap", "first" to clash.task, "second" to clash.other)
            is ScheduleWarning.BrokenLink -> args += mapOf("clash" to "link", "first" to clash.successor, "second" to clash.predecessor)
            null -> Unit
        }
        val kind = if (clash == null) NotificationKind.ASSISTANT_APPLIED else NotificationKind.ASSISTANT_CLASH
        notifications.add(kind, args, projectId, project?.name, undo)
    }
}

/** Tasks a change set changes (to highlight); deleted ones aren't there to highlight. */
fun List<ScheduleChange>.changedTaskIds(): Set<String> = mapNotNull {
    when (it) {
        is ScheduleChange.UpdateTask -> it.after.id
        is ScheduleChange.CreateTask -> it.task.id
        is ScheduleChange.AddLink -> it.link.successorId
        is ScheduleChange.RemoveLink -> it.link.successorId
        is ScheduleChange.DeleteTask -> null
    }
}.toSet()

/** How many tasks a change set touches. */
fun List<ScheduleChange>.taskCount(): Int = mapNotNull {
    when (it) {
        is ScheduleChange.UpdateTask -> it.after.id
        is ScheduleChange.CreateTask -> it.task.id
        is ScheduleChange.DeleteTask -> it.task.id
        is ScheduleChange.AddLink -> it.link.successorId
        is ScheduleChange.RemoveLink -> it.link.successorId
    }
}.toSet().size

/** Everything the assistant does, for one assistant (it owns one recorder and one player). */
class AssistantUseCases(
    val observeAvailability: ObserveVoiceAvailability,
    val checkServer: CheckVoiceServer,
    val notice: VoiceNotice,
    val introduction: ProjectIntroduction,
    val listen: ListenForSpeech,
    val play: PlaySpeech,
    val transcribe: TranscribeSpeech,
    val interpret: InterpretRequest,
    val explain: ExplainChanges,
    val voicePhrase: VoicePhrase,
    val recordDecision: RecordDecision,
    val apply: ApplyVoicePlan,
    val recordChange: RecordVoiceChange,
    val undo: UndoVoiceChange,
    val endSession: EndVoiceSession,
) {
    companion object {
        fun create(
            repository: ProjectRepository,
            voiceApi: VoiceApi,
            auth: AuthRepository,
            online: Flow<Boolean>,
            recorder: VoiceRecorder,
            player: SpeechPlayer,
            settings: AssistantSettings,
            notifications: NotificationRepository?,
            zone: ZoneId,
            clock: () -> Long,
            newId: () -> String,
        ): AssistantUseCases {
            val token = VoiceAccessToken(auth)
            val applyChanges = ApplyScheduleChanges(repository, zone)
            return AssistantUseCases(
                observeAvailability = ObserveVoiceAvailability(auth, online),
                checkServer = CheckVoiceServer(voiceApi),
                notice = VoiceNotice(settings),
                introduction = ProjectIntroduction(settings),
                listen = ListenForSpeech(recorder),
                play = PlaySpeech(player),
                transcribe = TranscribeSpeech(repository, voiceApi, token),
                interpret = InterpretRequest.create(repository, voiceApi, token, zone, clock, newId),
                explain = ExplainChanges(voiceApi, token, zone, clock),
                voicePhrase = VoicePhrase(voiceApi, token),
                recordDecision = RecordDecision(voiceApi, token, zone),
                apply = ApplyVoicePlan(applyChanges),
                recordChange = RecordVoiceChange(notifications, repository, zone),
                undo = UndoVoiceChange(applyChanges),
                endSession = EndVoiceSession(auth),
            )
        }
    }
}
