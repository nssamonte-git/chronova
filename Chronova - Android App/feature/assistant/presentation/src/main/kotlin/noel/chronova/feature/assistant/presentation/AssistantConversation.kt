package noel.chronova.feature.assistant.presentation

import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.feature.assistant.domain.AssistantUseCases
import noel.chronova.feature.assistant.domain.ConfirmationReply
import noel.chronova.feature.assistant.domain.DecisionAnswer
import noel.chronova.feature.assistant.domain.EndpointDetector
import noel.chronova.feature.assistant.domain.ExplainStage
import noel.chronova.feature.assistant.domain.ExplainedChange
import noel.chronova.feature.assistant.domain.Interpretation
import noel.chronova.feature.assistant.domain.Listened
import noel.chronova.feature.assistant.domain.PreviewItem
import noel.chronova.feature.assistant.domain.RecorderUnavailable
import noel.chronova.feature.assistant.domain.ScheduleWarning
import noel.chronova.feature.assistant.domain.VoiceApiException
import noel.chronova.feature.assistant.domain.VoicePlan
import noel.chronova.feature.assistant.domain.changedTaskIds
import noel.chronova.feature.assistant.domain.taskCount
import noel.chronova.feature.assistant.presentation.orb.OrbMode

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
}

/**
 * One spoken conversation. It greets, then listens until the speaker stops ([EndpointDetector]);
 * the server transcribes and interprets, and the app plans against the schedule as it is now
 * ([AssistantUseCases.interpret]). It says what it will change and asks; the spoken answer decides:
 * yes applies (through the one schedule apply path, refused if the schedule changed since), no
 * closes, anything else is a corrected request. Questions are asked aloud and the answer is folded
 * into the request. After applying, the orb closes first and then it says what changed while the
 * changed tasks are highlighted. Nothing is written without a spoken yes.
 *
 * Plain Kotlin: [AssistantViewModel] starts and cancels it, and everything the screen shows goes
 * through the shared [session].
 */
internal class AssistantConversation(
    private val projectId: String,
    private val useCases: AssistantUseCases,
    private val phrases: AssistantPhrases,
    private val zone: ZoneId,
    /** A hint for the transcriber: the phone's language, or null to let it detect. */
    private val language: () -> String?,
    private val session: AssistantSession,
) {
    private var greeting: Spoken? = null

    /** How loud the last words heard were ([EndpointDetector.loudestSpeech]). */
    private var lastLoudness = 0

    /** The language the user was last heard in, for what the assistant says back. */
    private var heardLanguage: String? = null

    /** The first-open introduction: only spoken; nothing is recorded and nothing of the user's is sent. */
    suspend fun introduce() = say(phrases.introduction())

    private sealed interface Outcome {
        data class Proposal(val plan: VoicePlan) : Outcome
        data class Question(val text: String) : Outcome

        /** [final]: nothing more can be done in this conversation. */
        data class Problem(val text: String, val final: Boolean = false) : Outcome
    }

    /** Greets, then listens and acts until the conversation ends (or is cancelled). */
    suspend fun run() {
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
                        val plan = outcome.plan
                        val alternative = plan.alternative
                        val heard = propose(plan) ?: return declined()
                        val (kind, reply) = answer(heard) ?: return declined()
                        val decided = when (kind) {
                            ConfirmationReply.Yes -> if (alternative != null) {
                                report(request, plan, DecisionAnswer.AcceptedFix)
                                return applyAndExplain(alternative)
                            } else {
                                report(request, plan, DecisionAnswer.Accepted)
                                return applyAndExplain(plan)
                            }
                            ConfirmationReply.No -> if (alternative == null) {
                                report(request, plan, DecisionAnswer.Declined)
                                return declined()
                            } else {
                                // They turned the alternative down: their own version is still open.
                                keepAsAsked(request, plan) ?: return
                            }
                            ConfirmationReply.Other -> reply
                        }
                        // A corrected request: worked out again with the first one as context.
                        report(request, plan, DecisionAnswer.Corrected, reply = decided)
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
            if (error.kind == VoiceApiException.Kind.UNAUTHORIZED) useCases.endSession()
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
        session.ui.update { it.copy(mode = OrbMode.Listening, saying = null) }
        val recorded = useCases.listen { session.level.value = it } as? Listened.Recorded ?: return Heard.Silence
        session.ui.update { it.copy(mode = OrbMode.Thinking) }
        val heard = useCases.transcribe(projectId, recorded.recording, language(), answering)
        heardLanguage = heard.language ?: language()
        val text = heard.text.trim()
        if (text.isEmpty()) return Heard.NoWords
        lastLoudness = recorded.loudestSpeech
        session.ui.update { it.copy(heard = text) }
        return Heard.Words(text)
    }

    private suspend fun interpret(text: String): Outcome {
        session.ui.update { it.copy(mode = OrbMode.Thinking) }
        return when (val interpreted = useCases.interpret(projectId, text)) {
            is Interpretation.Planned -> Outcome.Proposal(interpreted.plan)
            is Interpretation.Question -> Outcome.Question(interpreted.text)
            is Interpretation.Unsupported -> Outcome.Problem(phrases.unsupported(interpreted.reason))
            is Interpretation.CannotDo -> Outcome.Problem(phrases.cannotDo(interpreted.problem, interpreted.titles, interpreted.projectEndMillis))
            Interpretation.NothingToChange -> Outcome.Problem(phrases.nothingToChange())
            Interpretation.ScheduleChanged -> Outcome.Problem(phrases.scheduleChanged(), final = true)
            Interpretation.Unreadable -> Outcome.Problem(phrases.notUnderstood())
        }
    }

    /** Says what will change and asks; the spoken answer, or null if none came. */
    private suspend fun propose(proposal: VoicePlan): String? {
        val offer = proposal.alternative?.items.orEmpty()
        // On screen, only what the alternative does differently (it says the same).
        val asked = ExplainedChange.from(proposal.items, zone)
        val different = offer.filter { item -> ExplainedChange.from(listOf(item), zone).any { it !in asked } }
        session.ui.update { it.copy(proposal = proposal.items, warnings = proposal.warnings, offer = different) }
        // A clash is named before the question, which offers the alternative or asks whether to apply it anyway.
        speak(explain(proposal.items, ExplainStage.Proposal, proposal.warnings, offer))
        return listen(answering = true)
    }

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

    /**
     * After the alternative was turned down: asks whether to apply [proposal] as
     * asked. Applies it on yes; null when the conversation is over, or the words
     * of a corrected request.
     */
    private suspend fun keepAsAsked(request: String, proposal: VoicePlan): String? {
        session.ui.update { it.copy(offer = emptyList()) }
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
    private fun report(request: String, proposal: VoicePlan, answer: DecisionAnswer, reply: String? = null) {
        session.scope.launch { useCases.recordDecision(request, proposal, answer, reply) }
    }

    private suspend fun applyAndExplain(proposal: VoicePlan) {
        when (val result = useCases.apply(projectId, proposal)) {
            is ApplyResult.Rejected -> say(phrases.scheduleChanged())
            is ApplyResult.Applied -> {
                session.message.value = AssistantMessage.Applied(proposal.changes.taskCount(), result.undo)
                useCases.recordChange(projectId, proposal, result.undo)
                // Still busy while the orb gives way to the explanation: no gap in between.
                session.explaining.value = true
                // The orb goes first, so the user sees the timeline while hearing what changed.
                session.ui.update { it.copy(open = false, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
                val changed = proposal.changes.changedTaskIds()
                session.afterword = session.scope.launch {
                    session.highlight.value = changed
                    try {
                        speak(explain(proposal.items, ExplainStage.Applied), captioned = false)
                    } finally {
                        session.highlight.value = emptySet()
                        session.explaining.value = false
                    }
                }
            }
        }
    }

    /** The server's words (and audio) for [items]; null if it couldn't be reached. */
    private suspend fun explain(
        items: List<PreviewItem>,
        stage: ExplainStage,
        warnings: List<ScheduleWarning> = emptyList(),
        offer: List<PreviewItem> = emptyList(),
    ): Spoken? = useCases.explain(items, stage, heardLanguage, warnings, offer)?.let { Spoken(it.text, it.audio) }

    /** The proposal was turned down (or not answered): its lines go, so nothing looks applied. */
    private suspend fun declined() {
        session.ui.update { it.copy(proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
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

    private suspend fun voice(text: String): Spoken = useCases.voicePhrase(text, heardLanguage).let { Spoken(it.text, it.audio) }

    private data class Spoken(val text: String, val audio: ByteArray?)

    private suspend fun speak(spoken: Spoken?, captioned: Boolean = true) {
        if (spoken == null) return
        if (captioned) session.ui.update { it.copy(mode = OrbMode.Speaking, saying = spoken.text) }
        val audio = spoken.audio
        if (audio == null) {
            delay(readingTimeMs(spoken.text))
            return
        }
        useCases.play(audio)
    }

    /** Stops saying what changed (after an apply), and the highlight that goes with it. */
    fun stopAfterword() {
        session.afterword?.cancel()
        session.afterword = null
        useCases.play.stop()
        session.highlight.value = emptySet()
        session.explaining.value = false
    }

    private companion object {
        /** Two misses (nothing heard, couldn't do it) in a row end the conversation. */
        const val MAX_STRIKES = 2

        /**
         * How loud (the [EndpointDetector.loudestSpeech] of the answer) a "yes" must be to count at
         * once. Measured on the test phone (Android 10): requests said to the phone 8,900–10,200, a
         * one-word "yes" ~4,700–5,200, a "yes" at the volume of a voice across the room 1,400–2,500.
         * Lowered from 3,000 (user request, 12 Nov): a soft "yes" said to the phone was asked about
         * twice and nothing changed; the faintest voices across the room are still asked about.
         */
        const val NEAR_SPEECH_LEVEL = 2_000

        /** How long a caption stays when there's no audio: about 15 characters a second, at least 2 s. */
        fun readingTimeMs(text: String): Long = (text.length * 65L).coerceIn(2_000L, 8_000L)
    }
}
