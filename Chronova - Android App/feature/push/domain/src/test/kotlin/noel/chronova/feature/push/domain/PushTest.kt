package noel.chronova.feature.push.domain

import java.io.IOException
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.core.model.ReminderLead
import noel.chronova.core.testing.FakeAuthRepository
import noel.chronova.core.testing.FakeReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushTest {
    @Test
    fun aReminderNeedsItsTaskProjectTitleAndTimes() {
        val data = mapOf(
            "type" to "reminder", "taskId" to "t1", "projectId" to "p1", "projectName" to "Jnn", "title" to "Build",
            "milestone" to "false", "startMillis" to "1000", "atMillis" to "100",
        )
        assertEquals(PushMessage.Reminder("t1", "p1", "Jnn", "Build", false, 1000, 100), PushMessage.parse(data))
        assertEquals(true, (PushMessage.parse(data + ("milestone" to "true")) as PushMessage.Reminder).isMilestone)
        for (missing in listOf("taskId", "projectId", "title", "startMillis", "atMillis")) {
            assertNull(missing, PushMessage.parse(data - missing))
        }
        assertNull(PushMessage.parse(data + ("startMillis" to "soon")))
    }

    @Test
    fun alertsAndUnknownTypes() {
        assertEquals(PushMessage.SyncAvailable, PushMessage.parse(mapOf("type" to "sync_available")))
        assertEquals(PushMessage.SignedIn, PushMessage.parse(mapOf("type" to "signed_in")))
        assertEquals(PushMessage.AccountChanged, PushMessage.parse(mapOf("type" to "account_changed")))
        assertNull(PushMessage.parse(mapOf("type" to "from_the_future")))
        assertNull(PushMessage.parse(emptyMap()))
    }

    private class FakePushApi : PushApi {
        val registered = mutableListOf<Pair<String, DeviceRegistration>>()
        var fail = false

        override suspend fun register(accessToken: String, device: DeviceRegistration) {
            if (fail) throw IOException("offline")
            registered += accessToken to device
        }
    }

    @Test
    fun registersTheTokenZoneAndReminderChoiceWhenSignedIn() = runTest {
        val auth = FakeAuthRepository()
        val api = FakePushApi()
        val settings = FakeReminderSettings().also { it.setLead(ReminderLead.MINUTES_15) }
        val register = RegisterDevice(auth, api, tokens = { "fcm-1" }, settings = settings, zone = { ZoneId.of("Asia/Manila") })

        assertTrue(register())
        assertEquals("token-1" to DeviceRegistration("fcm-1", "Asia/Manila", ReminderLead.MINUTES_15), api.registered.single())
        // A token Firebase just handed over is used as it is.
        assertTrue(register("fcm-2"))
        assertEquals("fcm-2", api.registered.last().second.token)
        // UTC is named as servers accept it.
        RegisterDevice(auth, api, tokens = { "fcm-1" }, settings = settings, zone = { ZoneOffset.UTC })()
        assertEquals("UTC", api.registered.last().second.timeZone)
    }

    @Test
    fun signedOutWithoutATokenOrOfflineNothingIsRegistered() = runTest {
        val api = FakePushApi()
        val settings = FakeReminderSettings()
        assertFalse(RegisterDevice(FakeAuthRepository(AuthState.SignedOut), api, tokens = { "fcm-1" }, settings = settings)())
        assertFalse(RegisterDevice(FakeAuthRepository(), api, tokens = { null }, settings = settings)())
        api.fail = true
        assertFalse(RegisterDevice(FakeAuthRepository(), api, tokens = { "fcm-1" }, settings = settings)())
        assertTrue(api.registered.isEmpty())
    }

    @Test
    fun reminderChoicesHaveTheApisNames() {
        assertEquals(listOf("off", "at_start", "15_min", "1_hour", "1_day"), ReminderLead.entries.map { it.apiName() })
    }
}
