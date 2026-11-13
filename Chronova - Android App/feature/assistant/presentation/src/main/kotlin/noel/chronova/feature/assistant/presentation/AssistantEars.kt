package noel.chronova.feature.assistant.presentation

import kotlinx.coroutines.flow.update
import noel.chronova.feature.assistant.domain.AssistantUseCases
import noel.chronova.feature.assistant.domain.EndpointDetector
import noel.chronova.feature.assistant.domain.Listened
import noel.chronova.feature.assistant.presentation.orb.OrbMode

/** The assistant's listening: records until the speaker stops and has the words transcribed. */
internal class AssistantEars(
    /** The open project, or null on the list of projects. */
    private val projectId: String?,
    private val useCases: AssistantUseCases,
    /** A hint for the transcriber: the phone's language, or null to let it detect. */
    private val language: () -> String?,
    private val session: AssistantSession,
) {
    /** How loud the last words heard were ([EndpointDetector.loudestSpeech]). */
    var lastLoudness = 0
        private set

    /** The language the user was last heard in, for what the assistant says back. */
    var heardLanguage: String? = null
        private set

    /**
     * Listens until the user stops speaking; their words, or null if nothing was heard.
     * [answering]: a yes or no is expected, so the transcriber listens for those words too,
     * and a sound that turns out to hold no words (a cough, a door) gets one more listen
     * instead of counting as no answer.
     */
    suspend fun listen(answering: Boolean = false): String? {
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
}
