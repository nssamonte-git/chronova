package noel.chronova.app.data.remote

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
}
