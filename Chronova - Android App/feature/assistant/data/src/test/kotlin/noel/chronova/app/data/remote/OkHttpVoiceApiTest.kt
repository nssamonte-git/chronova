package noel.chronova.app.data.remote

import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.schedule.ScheduleState
import noel.chronova.app.domain.voice.ExplainedChange
import noel.chronova.app.domain.voice.ProposedOperation
import noel.chronova.app.domain.voice.ScheduleWarning
import noel.chronova.app.domain.voice.VoiceContext
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import noel.chronova.app.domain.voice.VoiceDecision
import noel.chronova.app.domain.voice.DecisionAnswer
import noel.chronova.app.domain.voice.Transcript
import noel.chronova.app.domain.voice.ExplainRequest
import noel.chronova.app.domain.voice.VoiceApiException

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
    fun theHealthCheckAsksUpWithoutAccountData() = runTest {
        reply(200)
        assertTrue(api.reachable())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/up", request.url.encodedPath)
        assertEquals(null, request.headers["Authorization"])
    }

    @Test
    fun anErrorFromTheHealthCheckCountsAsUnreachable() = runTest {
        reply(503)
        assertFalse(api.reachable())
    }

    @Test
    fun aServerThatIsGoneIsUnreachable() = runTest {
        server.close()
        assertFalse(api.reachable())
    }

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

        assertEquals(Transcript("push review by an hour", "en"), api.transcribe("token-1", audio, 2_400, "en", listOf("Build", "Design review")))

        val request = server.takeRequest()
        assertEquals("/v1/voice/transcribe", request.url.encodedPath)
        assertEquals("Bearer token-1", request.headers["Authorization"])
        assertTrue(request.headers["Content-Type"]!!.startsWith("multipart/form-data"))
        val body = request.body!!.utf8()
        assertTrue(body, body.contains("""name="audio"; filename="command.m4a""""))
        assertTrue(body, body.contains("Content-Type: audio/mp4"))
        assertTrue(body, body.contains("name=\"durationMs\"\r\n\r\n2400"))
        assertTrue(body, body.contains("name=\"language\""))
        assertTrue(body, body.contains("name=\"keyterms[]\"\r\n\r\nBuild\r\n"))
        assertTrue(body, body.contains("name=\"keyterms[]\"\r\n\r\nDesign review\r\n"))
        audio.delete()
    }

    @Test
    fun aTranscriptTooLongIsItsOwnProblem() = runTest {
        val audio = File.createTempFile("command", ".m4a")
        reply(422, """{"error":"transcript_too_long"}""")
        reply(422, """{"message":"The audio field is required."}""")
        listOf(VoiceApiException.Kind.TOO_LONG, VoiceApiException.Kind.REJECTED).forEach { kind ->
            try {
                api.transcribe("t", audio, 2_400, null, emptyList())
                fail("expected $kind")
            } catch (error: VoiceApiException) {
                assertEquals(kind, error.kind)
            }
        }
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body, !body.contains("name=\"language\""))
        audio.delete()
    }

    @Test
    fun explainSendsTheProposalsClashesWithExactlyTheirFields() = runTest {
        reply(200, """{"text":"Test will move to 4 p.m. Note that Test would overlap Build. Shall I apply it anyway?"}""")
        api.explain(
            "token-1",
            ExplainRequest.Changes(
                LocalDate.of(2026, 11, 12),
                listOf(ExplainedChange("removed", task = "QA")),
                null,
                warnings = listOf(ScheduleWarning.Overlap("Test", "Build"), ScheduleWarning.BrokenLink("Design", "Test")),
            ),
        )

        assertEquals(
            """{"today":"2026-11-12","changes":[{"change":"removed","task":"QA"}],"stage":"proposal",""" +
                """"warnings":[{"kind":"overlap","task":"Test","other":"Build"},{"kind":"dependency","predecessor":"Design","successor":"Test"}]}""",
            server.takeRequest().body!!.utf8(),
        )
    }

    @Test
    fun interpretReadsTheModelsAlternative() = runTest {
        reply(
            200,
            """{"revision":"r1","result":"operations","operations":[{"type":"shift_task","taskId":"t1","amount":2,"unit":"hours"}],""" +
                """"question":null,"reason":null,"alternative":[{"type":"move_task","taskId":"t1","date":"2026-11-13","time":null}]}""",
        )
        val proposal = api.interpret("token-1", "push design by two hours", context)
        assertEquals(listOf(ProposedOperation("move_task", taskId = "t1", date = "2026-11-13")), proposal.alternative)

        reply(200, """{"revision":"r1","result":"operations","operations":[{"type":"delete_task","taskId":"t1"}],"question":null,"reason":null}""")
        assertEquals(null, api.interpret("token-1", "delete design", context).alternative)
    }

    @Test
    fun resolveSendsThePlanWithEveryFieldAndTheClashes() = runTest {
        reply(200, """{"revision":"r1","result":"unsupported","operations":[],"question":null,"reason":"none"}""")
        val context = VoiceContext("p1", "Launch", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 18), "UTC", LocalDate.of(2026, 11, 12), LocalDateTime.of(2026, 11, 12, 9, 0), "r1", emptyList())
        val answer = api.resolve(
            "token-1",
            "move build to friday",
            context,
            listOf(ProposedOperation("move_task", taskId = "t1", date = "2026-11-13"), ProposedOperation("create_task", title = "QA", start = "2026-11-13T09:00", end = "2026-11-13T10:00")),
            listOf(ScheduleWarning.Overlap("Build", "QA")),
        )

        val request = server.takeRequest()
        assertEquals("/v1/voice/resolve", request.url.encodedPath)
        val body = request.body!!.utf8()
        assertTrue(body, body.contains(""""planned":[{"type":"move_task","taskId":"t1","date":"2026-11-13","time":null},""" +
            """{"type":"create_task","title":"QA","start":"2026-11-13T09:00","end":"2026-11-13T10:00","predecessorIds":[]}]"""))
        assertTrue(body, body.contains(""""clashes":[{"kind":"overlap","task":"Build","other":"QA"}]"""))
        assertTrue(body, body.startsWith("""{"transcript":"move build to friday","context":{"projectId":"p1""""))
        assertEquals("unsupported", answer.result)
    }

    @Test
    fun decideSendsTheAnswerAndAcceptsAnEmptyReply() = runTest {
        reply(204, "")
        api.decide(
            "token-1",
            VoiceDecision("drop qa", listOf(ExplainedChange("removed", task = "QA")), DecisionAnswer.Corrected, reply = "keep it"),
        )
        val request = server.takeRequest()
        assertEquals("/v1/voice/decision", request.url.encodedPath)
        assertEquals("""{"said":"drop qa","proposed":[{"change":"removed","task":"QA"}],"answer":"corrected","reply":"keep it"}""", request.body!!.utf8())
    }

    @Test
    fun explainSendsExactlyEachChangesFieldsAndDecodesTheAudio() = runTest {
        reply(200, """{"text":"Build moves to 11.","audio":"AQID","audioType":"audio/mpeg"}""")
        val answer = api.explain(
            "token-1",
            ExplainRequest.Changes(
                LocalDate.of(2026, 11, 12),
                listOf(
                    ExplainedChange("removed", task = "QA"),
                    ExplainedChange("added", task = "Demo", toStart = LocalDateTime.of(2026, 11, 13, 9, 0), toEnd = LocalDateTime.of(2026, 11, 13, 10, 0)),
                ),
                "en",
            ),
        )

        val request = server.takeRequest()
        assertEquals("/v1/voice/explain", request.url.encodedPath)
        assertEquals(
            """{"today":"2026-11-12","changes":[{"change":"removed","task":"QA"},""" +
                """{"change":"added","task":"Demo","toStart":"2026-11-13T09:00","toEnd":"2026-11-13T10:00"}],"language":"en","stage":"proposal"}""",
            request.body!!.utf8(),
        )
        assertEquals("Build moves to 11.", answer.text)
        assertEquals(listOf<Byte>(1, 2, 3), answer.audio!!.toList())
        assertEquals("audio/mpeg", answer.audioType)
    }

    @Test
    fun aQuestionIsSentAsItIsAndMissingOrBrokenAudioIsTextOnly() = runTest {
        reply(200, """{"text":"Which review?","audio":null,"audioType":null}""")
        reply(200, """{"text":"Which review?","audio":"%%%not base64","audioType":"audio/mpeg"}""")

        val first = api.explain("t", ExplainRequest.Question("Which review?", null))
        assertEquals("""{"question":"Which review?"}""", server.takeRequest().body!!.utf8())
        assertEquals(null, first.audio)

        val second = api.explain("t", ExplainRequest.Question("Which review?", null))
        assertEquals("Which review?", second.text)
        assertEquals(null, second.audio)
        assertEquals(null, second.audioType)
    }
}
