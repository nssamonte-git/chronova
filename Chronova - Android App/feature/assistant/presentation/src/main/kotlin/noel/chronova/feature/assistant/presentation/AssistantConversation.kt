package noel.chronova.feature.assistant.presentation

import noel.chronova.core.ui.assistant.ViewKind
import noel.chronova.core.ui.assistant.ViewColor
import noel.chronova.core.ui.assistant.ViewZoom
import noel.chronova.core.ui.assistant.ViewScale
import noel.chronova.core.ui.assistant.AssistantViewAction
import noel.chronova.feature.assistant.domain.VoiceAction
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
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
import noel.chronova.feature.assistant.domain.RecorderUnavailable
import noel.chronova.feature.assistant.domain.VoiceApiException
import noel.chronova.feature.assistant.domain.VoicePlan
import noel.chronova.feature.assistant.domain.changedTaskIds
import noel.chronova.feature.assistant.domain.taskCount
import noel.chronova.feature.assistant.presentation.orb.OrbMode

/**
 * One spoken conversation. It greets, then listens until the speaker stops ([EndpointDetector]);
 * the server transcribes and interprets, and the app plans against the schedule as it is now
 * ([AssistantUseCases.interpret]). It says what it will change and asks; the spoken answer decides:
 * yes applies (through the one schedule apply path, refused if the schedule changed since), no
 * closes, anything else is a corrected request. Questions are asked aloud and the answer is folded
 * into the request. After applying, the orb closes first and then it says what changed while the
 * changed tasks are highlighted. Nothing is written without a spoken yes. Actions (go to today, show
 * a task) change nothing, so they are done at once: alone, the orb closes and it says what it shows;
 * with changes, they are done before the changes are asked about.
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
    private val ears = AssistantEars(projectId, useCases, language, session)
    private val voice = AssistantVoice(useCases, phrases, session, heardLanguage = { ears.heardLanguage })

    /** The first-open introduction: only spoken; nothing is recorded and nothing of the user's is sent. */
    suspend fun introduce() = voice.say(phrases.introduction())

    private sealed interface Outcome {
        data class Proposal(val plan: VoicePlan, val actions: List<VoiceAction>) : Outcome

        /** Only things to show: done at once, and the conversation ends. */
        data class Act(val actions: List<VoiceAction>) : Outcome
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
            voice.greet()
            while (true) {
                val words = pendingWords ?: ears.listen() ?: run {
                    strikes++
                    if (strikes >= MAX_STRIKES) return voice.say(phrases.goodbye())
                    voice.say(phrases.nothingHeard())
                    null
                } ?: continue
                pendingWords = null
                val request = context?.let { "$it $words" } ?: words
                context = null
                when (val outcome = interpret(request)) {
                    is Outcome.Act -> return act(outcome.actions)
                    is Outcome.Proposal -> {
                        session.show(outcome.actions.map { it.toViewAction() })
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
                        voice.say(outcome.text)
                        context = "$request. Answer to \"${outcome.text}\":"
                    }
                    is Outcome.Problem -> {
                        strikes++
                        voice.say(outcome.text)
                        if (outcome.final || strikes >= MAX_STRIKES) return
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: VoiceApiException) {
            voice.say(phrases.failure(error.kind))
            // The server ended the session: the app goes back to the sign-in page.
            if (error.kind == VoiceApiException.Kind.UNAUTHORIZED) useCases.endSession()
        } catch (_: RecorderUnavailable) {
            voice.say(phrases.microphoneUnavailable())
        }
    }

    private suspend fun interpret(text: String): Outcome {
        session.ui.update { it.copy(mode = OrbMode.Thinking) }
        return when (val interpreted = useCases.interpret(projectId, text)) {
            is Interpretation.Planned -> Outcome.Proposal(interpreted.plan, interpreted.actions)
            is Interpretation.Act -> Outcome.Act(interpreted.actions)
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
        session.ui.update { state ->
            state.copy(proposal = proposal.items.map { it.toUi() }, warnings = proposal.warnings.map { it.toUi() }, offer = different.map { it.toUi() })
        }
        // A clash is named before the question, which offers the alternative or asks whether to apply it anyway.
        voice.speak(voice.explain(proposal.items, ExplainStage.Proposal, proposal.warnings, offer))
        return ears.listen(answering = true)
    }

    /**
     * Reads an answer to a yes/no question. A yes quieter than someone talking to the phone
     * ([NEAR_SPEECH_LEVEL]) may be a voice across the room (seen on the phone: a "yes" nobody
     * there meant applied a change), so it is asked about once more; null when there is
     * still no clear answer.
     */
    private suspend fun answer(heard: String): Pair<ConfirmationReply, String>? {
        val kind = ConfirmationReply.of(heard)
        if (kind != ConfirmationReply.Yes || ears.lastLoudness >= NEAR_SPEECH_LEVEL) return kind to heard
        voice.say(phrases.wasThatAYes())
        val again = ears.listen(answering = true) ?: return null
        val second = ConfirmationReply.of(again)
        if (second == ConfirmationReply.Yes && ears.lastLoudness < NEAR_SPEECH_LEVEL) return null
        return second to again
    }

    /**
     * After the alternative was turned down: asks whether to apply [proposal] as
     * asked. Applies it on yes; null when the conversation is over, or the words
     * of a corrected request.
     */
    private suspend fun keepAsAsked(request: String, proposal: VoicePlan): String? {
        session.ui.update { it.copy(offer = emptyList()) }
        voice.say(phrases.applyAsAsked())
        val heard = ears.listen(answering = true)
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
            is ApplyResult.Rejected -> voice.say(phrases.scheduleChanged())
            is ApplyResult.Applied -> {
                session.lastUndo = result.undo
                session.message.value = AssistantMessage.Applied(proposal.changes.taskCount())
                useCases.recordChange(projectId, proposal, result.undo)
                // Still busy while the orb gives way to the explanation: no gap in between.
                session.explaining.value = true
                // The orb goes first, so the user sees the timeline while hearing what changed.
                session.ui.update { it.copy(open = false, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
                val changed = proposal.changes.changedTaskIds()
                session.afterword = session.scope.launch {
                    session.highlight.value = changed
                    try {
                        voice.speak(voice.explain(proposal.items, ExplainStage.Applied), captioned = false)
                    } finally {
                        session.highlight.value = emptySet()
                        session.explaining.value = false
                    }
                }
            }
        }
    }

    /** Shows what was asked for: the orb goes first, so the user sees it while hearing what is shown. */
    private fun act(actions: List<VoiceAction>) {
        session.ui.update { it.copy(open = false, proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
        session.show(actions.map { it.toViewAction() })
        session.explaining.value = true
        session.afterword = session.scope.launch {
            try {
                voice.sayAside(phrases.showing(actions.last()))
            } finally {
                session.explaining.value = false
            }
        }
    }

    /** The proposal was turned down (or not answered): its lines go, so nothing looks applied. */
    private suspend fun declined() {
        session.ui.update { it.copy(proposal = emptyList(), warnings = emptyList(), offer = emptyList()) }
        voice.say(phrases.nothingChanged())
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
    }
}

/** The screen's form of an action (the timeline knows only [AssistantViewAction]). */
internal fun VoiceAction.toViewAction(): AssistantViewAction = when (this) {
    VoiceAction.GoToToday -> AssistantViewAction.GoToToday
    is VoiceAction.GoToDate -> AssistantViewAction.GoToDate(date)
    is VoiceAction.ShowTask -> AssistantViewAction.ShowTask(taskId)
    is VoiceAction.SetScale -> AssistantViewAction.SetScale(ViewScale.valueOf(scale.name))
    is VoiceAction.Zoom -> AssistantViewAction.Zoom(ViewZoom.valueOf(step.name))
    is VoiceAction.RowHeight -> AssistantViewAction.RowHeight(bigger)
    is VoiceAction.CriticalPath -> AssistantViewAction.CriticalPath(on)
    is VoiceAction.Search -> AssistantViewAction.Search(
        query,
        colors.mapTo(mutableSetOf()) { ViewColor.valueOf(it.name) },
        kinds.mapTo(mutableSetOf()) { ViewKind.valueOf(it.name) },
    )
    VoiceAction.ClearSearch -> AssistantViewAction.ClearSearch
    is VoiceAction.OpenTask -> AssistantViewAction.OpenTask(taskId)
}
