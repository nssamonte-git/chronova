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

/** The assistant's remembered settings: whether the user has seen the one-time privacy notice. */
interface AssistantSettings {
    var noticeAccepted: Boolean
}

class PreferencesAssistantSettings(private val prefs: SharedPreferences) : AssistantSettings {
    override var noticeAccepted: Boolean
        get() = prefs.getBoolean(KEY_NOTICE, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTICE, value) }

    private companion object {
        const val KEY_NOTICE = "voice_notice_accepted"
    }
}
