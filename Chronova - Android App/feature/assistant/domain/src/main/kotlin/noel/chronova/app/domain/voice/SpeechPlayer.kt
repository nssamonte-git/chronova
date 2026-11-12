package noel.chronova.app.domain.voice

import kotlinx.coroutines.flow.StateFlow

/** Plays the assistant's short spoken answers, one at a time. */
interface SpeechPlayer {
    /** True while an answer is playing. */
    val playing: StateFlow<Boolean>

    /** Plays [audio] (MP3 or another format Android decodes), stopping whatever was playing. */
    fun play(audio: ByteArray)

    fun stop()

    /** Frees the player for good. */
    fun release()
}

/** The assistant's remembered settings: whether the user has seen the one-time privacy notice. */
interface AssistantSettings {
    var noticeAccepted: Boolean

    /** Whether the assistant has introduced itself in this project (it does so on the first open). */
    fun introduced(projectId: String): Boolean
    fun markIntroduced(projectId: String)
}
