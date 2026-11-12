package noel.chronova.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SpeechPlayer] on [MediaPlayer], as an assistant voice: other audio ducks
 * (or pauses) while it speaks and resumes after, and it stops if another app
 * takes audio focus. The answer is written to the app's cache to play and
 * deleted when done. Call from the main thread.
 */
class AndroidSpeechPlayer(private val context: Context) : SpeechPlayer {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop() }
        .build()
    private val file = File(context.cacheDir, "voice/answer.audio")
    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = _playing.asStateFlow()

    override fun play(audio: ByteArray) {
        stop()
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return // e.g. during a call
        val player = MediaPlayer()
        try {
            file.parentFile?.mkdirs()
            file.writeBytes(audio)
            player.setAudioAttributes(attributes)
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { stop() }
            player.setOnErrorListener { _, _, _ -> stop().let { true } }
            player.prepareAsync()
        } catch (_: IOException) {
            player.release()
            finish()
            return
        } catch (_: IllegalStateException) {
            player.release()
            finish()
            return
        }
        this.player = player
        _playing.value = true
        Log.i("ChronovaVoice", "speaking")
    }

    override fun stop() {
        player?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
                // Still preparing; releasing is enough.
            }
            it.release()
        }
        player = null
        finish()
    }

    override fun release() = stop()

    private fun finish() {
        if (_playing.value) Log.i("ChronovaVoice", "spoken")
        audioManager.abandonAudioFocusRequest(focus)
        file.delete()
        _playing.value = false
    }
}
