package noel.chronova.feature.assistant.domain

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenForSpeechTest {

    /** Quiet (300) until [speaksAtMs], then a voice (8,000) for a second, then quiet; one sample per 100 ms. */
    private class LateSpeaker(private val speaksAtMs: Long) : VoiceRecorder {
        var cancelled = false
        private var elapsed = 0L

        override fun start() {
            elapsed = 0
        }

        override fun amplitude(): Int {
            elapsed += ListenForSpeech.SAMPLE_MS
            return if (elapsed in speaksAtMs until speaksAtMs + 1_000) 8_000 else 300
        }

        override fun stop(): Recording = Recording(File.createTempFile("answer", ".m4a").apply { deleteOnExit() }, elapsed)

        override fun cancel() {
            cancelled = true
        }
    }

    @Test
    fun aRequestStillGivesUpAfterEightSecondsOfSilence() = runTest {
        val recorder = LateSpeaker(speaksAtMs = 12_000)
        val heard = ListenForSpeech(recorder)(ListenForSpeech.REQUEST_WAIT_MS) {}
        assertEquals(Listened.NothingHeard, heard)
        assertTrue(recorder.cancelled)
    }

    @Test
    fun anAnswerSaidAfterReadingALongProposalIsStillHeard() = runTest {
        // Twelve seconds to read four changes, then "yes": heard, not taken as silence (no).
        val heard = ListenForSpeech(LateSpeaker(speaksAtMs = 12_000))(ListenForSpeech.ANSWER_WAIT_MS) {}
        assertTrue(heard is Listened.Recorded)
    }

    @Test
    fun anAnswerThatNeverComesEndsAtTheLongerLimit() = runTest {
        val recorder = LateSpeaker(speaksAtMs = 60_000)
        val heard = ListenForSpeech(recorder)(ListenForSpeech.ANSWER_WAIT_MS) {}
        assertEquals(Listened.NothingHeard, heard)
        assertEquals(20_000L, testScheduler.currentTime)
    }
}
