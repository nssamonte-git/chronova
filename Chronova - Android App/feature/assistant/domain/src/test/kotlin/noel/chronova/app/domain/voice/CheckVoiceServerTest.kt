package noel.chronova.app.domain.voice

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckVoiceServerTest {
    /** Only the health check matters here. */
    private class HealthOnly(private val answer: () -> Boolean) : VoiceApi {
        override suspend fun reachable() = answer()
        override suspend fun transcribe(accessToken: String, audio: File, durationMs: Long, language: String?, keyterms: List<String>) = error("unused")
        override suspend fun explain(accessToken: String, request: ExplainRequest) = error("unused")
        override suspend fun interpret(accessToken: String, transcript: String, context: VoiceContext) = error("unused")
        override suspend fun resolve(accessToken: String, transcript: String, context: VoiceContext, planned: List<ProposedOperation>, clashes: List<ScheduleWarning>) = error("unused")
        override suspend fun decide(accessToken: String, decision: VoiceDecision) = error("unused")
    }

    @Test
    fun anAnswerFromTheServerCountsAsReachable() = runTest {
        assertTrue(CheckVoiceServer(HealthOnly { true })())
        assertFalse(CheckVoiceServer(HealthOnly { false })())
    }

    @Test
    fun anyFailureCountsAsUnreachable() = runTest {
        assertFalse(CheckVoiceServer(HealthOnly { throw IOException("refused") })())
        assertFalse(CheckVoiceServer(HealthOnly { throw IllegalStateException("odd") })())
    }
}
