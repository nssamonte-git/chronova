package noel.chronova.feature.assistant.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import noel.chronova.core.domain.repository.ProjectRepository

// Hearing and speaking: recording until the speaker stops, playing the voiced replies, and transcription.

/** What listening produced. */
sealed interface Listened {
    /** Nobody spoke before the no-speech limit (or nothing usable was recorded). */
    data object NothingHeard : Listened

    /** A recording, and how loud its loudest speech was ([EndpointDetector.loudestSpeech]). */
    data class Recorded(val recording: Recording, val loudestSpeech: Int) : Listened
}

/**
 * Records from the microphone until the speaker stops ([EndpointDetector]), reporting the
 * level (0..1) for the orb's pulse as it goes. @throws RecorderUnavailable if the microphone can't be used.
 */
class ListenForSpeech(private val recorder: VoiceRecorder, private val sampleMs: Long = SAMPLE_MS) {
    suspend operator fun invoke(onLevel: (Float) -> Unit): Listened {
        recorder.start()
        val detector = EndpointDetector()
        var elapsed = 0L
        var decision: EndpointDetector.Decision = EndpointDetector.Decision.Continue
        try {
            while (decision == EndpointDetector.Decision.Continue) {
                delay(sampleMs)
                elapsed += sampleMs
                val amplitude = recorder.amplitude()
                onLevel(detector.level(amplitude))
                decision = detector.sample(amplitude, elapsed)
            }
        } finally {
            onLevel(0f)
        }
        if (decision == EndpointDetector.Decision.NothingHeard) {
            recorder.cancel()
            return Listened.NothingHeard
        }
        val recording = recorder.stop() ?: return Listened.NothingHeard
        return Listened.Recorded(recording, detector.loudestSpeech)
    }

    /** Stops and throws away any recording in progress. */
    fun cancel() = recorder.cancel()

    companion object {
        const val SAMPLE_MS = 100L
    }
}

/** Plays the assistant's spoken answers, one at a time. */
class PlaySpeech(private val player: SpeechPlayer) {
    /** Plays [audio] and returns when it has finished (or was stopped). */
    suspend operator fun invoke(audio: ByteArray) {
        player.play(audio)
        player.playing.first { !it }
    }

    fun stop() = player.stop()
    fun release() = player.release()
}

/**
 * Sends a recording to be transcribed, with the project's task titles (and, when a yes or
 * no is expected, those words) as terms to listen for. The recording is deleted either way.
 */
class TranscribeSpeech(
    private val repository: ProjectRepository,
    private val voiceApi: VoiceApi,
    private val token: VoiceAccessToken,
) {
    suspend operator fun invoke(projectId: String, recording: Recording, language: String?, answering: Boolean): Transcript {
        try {
            val titles = repository.schedule(projectId)?.tasks?.map { it.title }.orEmpty()
            val keyterms = if (answering) ConfirmationReply.KEYTERMS + titles else titles
            return voiceApi.transcribe(token(), recording.file, recording.durationMs, language, keyterms)
        } finally {
            recording.file.delete()
        }
    }
}
