package noel.chronova.feature.assistant.domain

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

// What the assistant says back: explaining changes, voicing phrases, and reporting how the user answered.

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
