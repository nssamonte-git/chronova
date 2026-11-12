package noel.chronova.feature.assistant.domain

import java.io.File

/** One finished recording: AAC in an MPEG-4 file, deleted by whoever uploads it. */
data class Recording(val file: File, val durationMs: Long)

/** Records one spoken command at a time from the microphone. Needs RECORD_AUDIO. */
interface VoiceRecorder {
    /** Starts a new recording. @throws RecorderUnavailable if the microphone can't be used. */
    fun start()

    /** Stops and returns the recording, or null if nothing usable was recorded (too short, or it failed). */
    fun stop(): Recording?

    /** Stops and throws the recording away. Safe to call when not recording. */
    fun cancel()

    /** The loudest level (0..32767) since the last call; 0 when not recording. */
    fun amplitude(): Int
}

class RecorderUnavailable(cause: Throwable) : Exception("The microphone can't be used.", cause)
