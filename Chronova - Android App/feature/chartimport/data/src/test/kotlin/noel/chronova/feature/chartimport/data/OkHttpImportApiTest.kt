package noel.chronova.feature.chartimport.data

import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import noel.chronova.core.model.Project
import noel.chronova.feature.chartimport.domain.ImportApiException
import noel.chronova.feature.chartimport.domain.ImportedSchedule
import noel.chronova.feature.chartimport.domain.ImportedTask
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpImportApiTest {
    private val server = MockWebServer()
    private lateinit var api: OkHttpImportApi
    private val today = LocalDate.of(2026, 11, 13)

    @Before
    fun setUp() {
        server.start()
        api = OkHttpImportApi(server.url("/").toString(), OkHttpClient())
    }

    @After
    fun tearDown() = server.close()

    private fun reply(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun sendsTheImageAndContextAndReadsTheSchedule() = runTest {
        reply(
            200,
            """{"result":"ok","dates":"relative","project":{"name":"Project Timeline","startDate":null,"endDate":null,"days":49},
            "tasks":[{"ref":"t1","title":"Install","startDate":null,"endDate":null,"startDay":42,"endDay":48,"milestone":false,"progress":0,"predecessorRefs":["t0"]}],
            "uncertain":["Row 4 was blurred."],"extra":"ignored"}""",
        )
        val project = Project("p1", "Office move", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 31))

        val schedule = api.read("token-1", byteArrayOf(1, 2, 3), "Asia/Manila", today, project)

        assertEquals(
            ImportedSchedule(
                ImportedSchedule.Result.OK, ImportedSchedule.Dates.RELATIVE, "Project Timeline", null, null, 49,
                listOf(ImportedTask("t1", "Install", null, null, 42, 48, false, 0, listOf("t0"))),
                listOf("Row 4 was blurred."),
            ),
            schedule,
        )
        val request = server.takeRequest()
        assertEquals("/v1/import/image", request.url.encodedPath)
        assertEquals("Bearer token-1", request.headers["Authorization"])
        val body = request.body!!.utf8()
        for (part in listOf(
            "name=\"image\"; filename=\"chart.jpg\"", "Content-Type: image/jpeg", "name=\"mode\"", "add_to_project",
            "name=\"timeZone\"", "Asia/Manila", "2026-11-13", "name=\"projectName\"", "Office move", "2026-11-16", "2026-12-31",
        )) {
            assertTrue(part, body.contains(part))
        }
    }

    @Test
    fun aNewProjectSendsNoProjectAndCalendarDatesAreRead() = runTest {
        reply(
            200,
            """{"result":"ok","dates":"absolute","project":{"name":"","startDate":"2026-03-02","endDate":"2026-03-13","days":null},
            "tasks":[{"ref":"m1","title":"Go live","startDate":"2026-03-13","endDate":"2026-03-13","startDay":null,"endDay":null,"milestone":true,"progress":100,"predecessorRefs":[]}],"uncertain":[]}""",
        )
        val schedule = api.read("t", byteArrayOf(1), "UTC", today, null)
        assertEquals(LocalDate.of(2026, 3, 2), schedule.startDate)
        assertEquals(true, schedule.tasks.single().isMilestone)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("new_project"))
        assertTrue(!body.contains("projectName"))
    }

    @Test
    fun theHoursWrittenOnAOneDayTaskAreRead() = runTest {
        fun task(ref: String, hours: String) =
            """{"ref":"$ref","title":"Menu Tasting","startDate":"2026-11-26","endDate":"2026-11-26","startDay":null,"endDay":null,$hours"milestone":false,"progress":0,"predecessorRefs":[]}"""
        reply(
            200,
            """{"result":"ok","dates":"absolute","project":{"name":"","startDate":"2026-11-26","endDate":"2026-11-26","days":null},"tasks":[
            ${task("t1", """"startTime":"10:00","endTime":"13:00",""")},
            ${task("t2", """"startTime":null,"endTime":null,""")},
            ${task("t3", "")},
            ${task("t4", """"startTime":"10am","endTime":"13:00",""")}],"uncertain":[]}""",
        )
        val tasks = api.read("t", byteArrayOf(1), "UTC", today, null).tasks

        assertEquals(LocalTime.of(10, 0) to LocalTime.of(13, 0), tasks[0].startTime to tasks[0].endTime)
        // No hours, a server from before them, or hours this app can't read: the task keeps its day.
        for (task in tasks.drop(1)) assertEquals(task.ref, null to null, task.startTime to task.endTime)
    }

    @Test
    fun errorsBecomeKinds() = runTest {
        val cases = listOf(
            401 to "" to ImportApiException.Kind.UNAUTHORIZED,
            429 to "" to ImportApiException.Kind.RATE_LIMITED,
            503 to """{"error":"import_disabled"}""" to ImportApiException.Kind.DISABLED,
            422 to """{"error":"invalid_image"}""" to ImportApiException.Kind.BAD_IMAGE,
            422 to """{"errors":{"image":["The image field must not be greater than 4096 kilobytes."]}}""" to ImportApiException.Kind.BAD_IMAGE,
            502 to """{"error":"provider_unavailable"}""" to ImportApiException.Kind.UNAVAILABLE,
            200 to """{"result":"maybe","dates":"relative","project":{},"tasks":[]}""" to ImportApiException.Kind.UNAVAILABLE,
            200 to """{"result":"ok","dates":"absolute","project":{"startDate":"13/03/2026"},"tasks":[]}""" to ImportApiException.Kind.UNAVAILABLE,
            200 to "not json" to ImportApiException.Kind.UNAVAILABLE,
        )
        for ((answer, kind) in cases) {
            reply(answer.first, answer.second)
            val error = runCatching { api.read("t", byteArrayOf(1), "UTC", today, null) }.exceptionOrNull()
            assertEquals("${answer.first} ${answer.second}", kind, (error as ImportApiException).kind)
        }
        server.close()
        val offline = runCatching { api.read("t", byteArrayOf(1), "UTC", today, null) }.exceptionOrNull()
        assertEquals(ImportApiException.Kind.NETWORK, (offline as ImportApiException).kind)
    }
}
