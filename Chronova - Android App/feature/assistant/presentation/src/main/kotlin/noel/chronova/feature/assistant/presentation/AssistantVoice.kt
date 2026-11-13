package noel.chronova.feature.assistant.presentation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import noel.chronova.feature.assistant.domain.AssistantUseCases
import noel.chronova.feature.assistant.domain.ExplainStage
import noel.chronova.feature.assistant.domain.PreviewItem
import noel.chronova.feature.assistant.domain.ScheduleWarning
import noel.chronova.feature.assistant.presentation.orb.OrbMode

/** The assistant's voice: what it says, voiced by the server when it can, captioned on the orb. */
internal class AssistantVoice(
    private val useCases: AssistantUseCases,
    private val phrases: AssistantPhrases,
    private val session: AssistantSession,
    /** The language the user was last heard in, for what the assistant says back. */
    private val heardLanguage: () -> String?,
) {
    private var greeting: Spoken? = null

    /** The server's words (and audio) for [items]; null if it couldn't be reached. */
    suspend fun explain(
        items: List<PreviewItem>,
        stage: ExplainStage,
        warnings: List<ScheduleWarning> = emptyList(),
        offer: List<PreviewItem> = emptyList(),
    ): Spoken? = useCases.explain(items, stage, heardLanguage(), warnings, offer)?.let { Spoken(it.text, it.audio) }

    /** Says hello when the orb opens. The voiced greeting is fetched once and replayed after that. */
    suspend fun greet() {
        val cached = greeting
        if (cached != null) return speak(cached)
        val spoken = voice(phrases.greeting())
        if (spoken.audio != null) greeting = spoken
        speak(spoken)
    }

    /** Says [text] in the assistant's voice (falls back to a caption alone). */
    suspend fun say(text: String) = speak(voice(text))

    /** Says [text] with the orb closed: heard, not captioned. */
    suspend fun sayAside(text: String) = speak(voice(text), captioned = false)

    private suspend fun voice(text: String): Spoken = useCases.voicePhrase(text, heardLanguage()).let { Spoken(it.text, it.audio) }

    data class Spoken(val text: String, val audio: ByteArray?)

    suspend fun speak(spoken: Spoken?, captioned: Boolean = true) {
        if (spoken == null) return
        if (captioned) session.ui.update { it.copy(mode = OrbMode.Speaking, saying = spoken.text) }
        val audio = spoken.audio
        if (audio == null) {
            delay(readingTimeMs(spoken.text))
            return
        }
        useCases.play(audio)
    }

    private companion object {
        /** How long a caption stays when there's no audio: about 15 characters a second, at least 2 s. */
        fun readingTimeMs(text: String): Long = (text.length * 65L).coerceIn(2_000L, 8_000L)
    }
}
