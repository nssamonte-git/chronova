package noel.chronova.app.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * [VoiceRecorder] on [MediaRecorder]: 16 kHz mono AAC in MPEG-4, ~32 kbit/s,
 * tuned for speech recognition. Files live in the app's cache (`voice/`) and
 * are deleted after upload or on cancel; leftovers are cleared on the next start.
 */
class AndroidVoiceRecorder(private val context: Context) : VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    override fun start() {
        cancel()
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val output = File.createTempFile("command", ".m4a", dir)
        val recorder = newRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(16_000)
            recorder.setAudioEncodingBitRate(32_000)
            // A safety net: the caller stops at its own limit first.
            recorder.setMaxDuration(MAX_MS)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
            recorder.start()
        } catch (error: IOException) {
            fail(recorder, output, error)
        } catch (error: RuntimeException) {
            // IllegalStateException, or "start failed" when another app holds the microphone.
            fail(recorder, output, error)
        }
        this.recorder = recorder
        this.file = output
        startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "listening") // state only, never content
    }

    override fun stop(): Recording? {
        val recorder = recorder ?: return null
        val output = file ?: return null
        val durationMs = SystemClock.elapsedRealtime() - startedAt
        Log.i(TAG, "stopped")
        this.recorder = null
        this.file = null
        val ok = try {
            recorder.stop()
            true
        } catch (_: RuntimeException) {
            // Thrown when stopped before any audio was written.
            false
        } finally {
            recorder.release()
        }
        if (!ok || output.length() == 0L) {
            output.delete()
            return null
        }
        return Recording(output, durationMs.coerceAtMost(MAX_MS.toLong()))
    }

    override fun amplitude(): Int = try {
        (recorder?.maxAmplitude ?: 0).also { Log.v(TAG, "level $it") } // a number only, for tuning
    } catch (_: IllegalStateException) {
        0
    }

    override fun cancel() {
        if (recorder != null) Log.i(TAG, "cancelled")
        recorder?.let { recorder ->
            try {
                recorder.stop()
            } catch (_: RuntimeException) {
                // Nothing recorded yet; releasing is enough.
            }
            recorder.release()
        }
        file?.delete()
        recorder = null
        file = null
    }

    private fun fail(recorder: MediaRecorder, output: File, error: Throwable): Nothing {
        recorder.release()
        output.delete()
        throw RecorderUnavailable(error)
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    private companion object {
        const val TAG = "ChronovaVoice"
        const val MAX_MS = 61_000
    }
}
