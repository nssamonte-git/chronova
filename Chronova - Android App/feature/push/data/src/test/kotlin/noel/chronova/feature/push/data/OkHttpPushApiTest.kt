package noel.chronova.feature.push.data

import java.io.IOException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import noel.chronova.core.model.ReminderLead
import noel.chronova.feature.push.domain.DeviceRegistration
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OkHttpPushApiTest {
    private val server = MockWebServer()
    private lateinit var api: OkHttpPushApi

    @Before
    fun setUp() {
        server.start()
        api = OkHttpPushApi(server.url("/").toString(), OkHttpClient())
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun registersTheDeviceWithItsSettings() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        api.register("token-1", DeviceRegistration("fcm-1", "Asia/Manila", ReminderLead.HOUR_1))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/devices", request.url.encodedPath)
        assertEquals("Bearer token-1", request.headers["Authorization"])
        assertEquals(
            """{"token":"fcm-1","platform":"android","timeZone":"Asia/Manila","reminderLead":"1_hour"}""",
            request.body!!.utf8(),
        )
    }

    @Test
    fun aRefusalIsAFailure() = runTest {
        server.enqueue(MockResponse.Builder().code(422).build())
        try {
            api.register("token-1", DeviceRegistration("fcm-1", "UTC", ReminderLead.OFF))
            fail("expected a failure")
        } catch (_: IOException) {
        }
    }
}
