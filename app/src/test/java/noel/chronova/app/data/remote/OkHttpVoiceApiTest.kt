package noel.chronova.app.data.remote

import java.io.File
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.schedule.ScheduleState
import noel.chronova.app.domain.voice.ProposedOperation
import noel.chronova.app.domain.voice.VoiceContext
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OkHttpVoiceApiTest {
    private val server = MockWebServer()
    private lateinit var api: OkHttpVoiceApi
    private val zone = ZoneOffset.UTC
    private val context = VoiceContext.from(
        ScheduleState(SampleTasks.project, SampleTasks.tasks(zone), emptyList()),
        zone,
        SampleTasks.REFERENCE_DATE.atTime(8, 5, 30).toInstant(zone).toEpochMilli(),
    )

    @Before
    fun setUp() {
        server.start()
        api = OkHttpVoiceApi(server.url("/").toString(), OkHttpClient())
    }

    @After
    fun tearDown() = server.close()

    private fun reply(code: Int, body: String = "") = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun sendsLocalTimesAndReadsTheProposal() = runTest {
        reply(
            200,
            """{"revision":"${context.revision}","result":"operations","operations":[
               {"type":"move_task","taskId":"review","date":"2026-03-03","time":null},
               {"type":"add_dependency","predecessorId":"ui-design","successorId":"review"}],
               "question":null,"reason":null,"extra":"ignored"}""",
        )
        val proposal = api.interpret("token-1", "move review to tomorrow", context)

        val request = server.takeRequest()
        assertEquals("/v1/voice/interpret", request.url.encodedPath)
        assertEquals("Bearer token-1", request.headers["Authorization"])
        val body = request.body!!.utf8()
        assertTrue(body, body.contains(""""transcript":"move review to tomorrow""""))
        assertTrue(body, body.contains(""""now":"2026-03-02T08:05""""))
        assertTrue(body, body.contains(""""start":"2026-03-02T09:00","end":"2026-03-02T11:00""""))
        assertTrue(body, body.contains(""""timeZone":"UTC""""))

        assertEquals(context.revision, proposal.revision)
        assertEquals(
            listOf(
                ProposedOperation("move_task", taskId = "review", date = "2026-03-03"),
                ProposedOperation("add_dependency", predecessorId = "ui-design", successorId = "review"),
            ),
            proposal.operations,
        )
    }

    @Test
    fun statusesMapToKinds() = runTest {
        mapOf(
            401 to VoiceApiException.Kind.UNAUTHORIZED,
            422 to VoiceApiException.Kind.REJECTED,
            429 to VoiceApiException.Kind.RATE_LIMITED,
            502 to VoiceApiException.Kind.UNAVAILABLE,
            503 to VoiceApiException.Kind.DISABLED,
        ).forEach { (code, kind) ->
            reply(code, """{"error":"x"}""")
            try {
                api.interpret("t", "x", context)
                fail("expected $kind")
            } catch (error: VoiceApiException) {
                assertEquals(kind, error.kind)
            }
        }
    }

    @Test
    fun aGarbledAnswerIsUnavailable() = runTest {
        reply(200, "<html>proxy error</html>")
        try {
            api.interpret("t", "x", context)
            fail()
        } catch (error: VoiceApiException) {
            assertEquals(VoiceApiException.Kind.UNAVAILABLE, error.kind)
        }
    }

    @Test
    fun uploadsTheRecordingAsAForm() = runTest {
        reply(200, """{"text":" push review by an hour ","language":"en"}""")
        val audio = File.createTempFile("command", ".m4a").apply { writeBytes(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112)) }

        assertEquals("push review by an hour", api.transcribe("token-1", audio, 2_400, "en"))

        val request = server.takeRequest()
        assertEquals("/v1/voice/transcribe", request.url.encodedPath)
        assertEquals("Bearer token-1", request.headers["Authorization"])
        assertTrue(request.headers["Content-Type"]!!.startsWith("multipart/form-data"))
        val body = request.body!!.utf8()
        assertTrue(body, body.contains("""name="audio"; filename="command.m4a""""))
        assertTrue(body, body.contains("Content-Type: audio/mp4"))
        assertTrue(body, body.contains("name=\"durationMs\"\r\n\r\n2400"))
        assertTrue(body, body.contains("name=\"language\""))
        audio.delete()
    }

    @Test
    fun aTranscriptTooLongIsItsOwnProblem() = runTest {
        val audio = File.createTempFile("command", ".m4a")
        reply(422, """{"error":"transcript_too_long"}""")
        reply(422, """{"message":"The audio field is required."}""")
        listOf(VoiceApiException.Kind.TOO_LONG, VoiceApiException.Kind.REJECTED).forEach { kind ->
            try {
                api.transcribe("t", audio, 2_400, null)
                fail("expected $kind")
            } catch (error: VoiceApiException) {
                assertEquals(kind, error.kind)
            }
        }
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body, !body.contains("name=\"language\""))
        audio.delete()
    }
}
