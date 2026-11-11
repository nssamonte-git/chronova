package noel.chronova.app.voice

import android.content.SharedPreferences
import androidx.core.content.edit
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

/** Whether the assistant speaks its answers; remembered across launches. */
interface SpeechSettings {
    var muted: Boolean
}

class PreferencesSpeechSettings(private val prefs: SharedPreferences) : SpeechSettings {
    override var muted: Boolean
        get() = prefs.getBoolean(KEY_MUTED, false)
        set(value) = prefs.edit { putBoolean(KEY_MUTED, value) }

    private companion object {
        const val KEY_MUTED = "spoken_answers_muted"
    }
}
