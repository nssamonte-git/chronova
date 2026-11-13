package noel.chronova.feature.push.domain

import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import noel.chronova.core.domain.auth.AuthRepository
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.core.domain.reminders.ReminderSettingsQueries
import noel.chronova.core.model.ReminderLead

/** What the server needs to push to this device (`PUT /v1/devices`). */
data class DeviceRegistration(val token: String, val timeZone: String, val reminderLead: ReminderLead) {
    val platform: String get() = "android"
}

/** The server's devices endpoint. */
interface PushApi {
    suspend fun register(accessToken: String, device: DeviceRegistration)
}

/** This device's push token (Firebase); null when it can't be had (no Play services, offline). */
fun interface PushTokens {
    suspend fun current(): String?
}

/**
 * Tells the server how to reach this device and when it wants reminders: on sign-in, a new token,
 * a changed reminder setting and each start of the app. Best effort: signed out, or when the server
 * can't be reached, nothing happens (the next trigger tries again). Signing out needs nothing here:
 * the server forgets a device with its sign-in.
 */
class RegisterDevice(
    private val auth: AuthRepository,
    private val api: PushApi,
    private val tokens: PushTokens,
    private val settings: ReminderSettingsQueries,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** [token]: a token Firebase just handed over, or null to ask for the current one. True when registered. */
    suspend operator fun invoke(token: String? = null): Boolean {
        if (auth.state.value !is AuthState.SignedIn) return false
        return try {
            val accessToken = auth.accessToken() ?: return false
            val pushToken = token ?: tokens.current() ?: return false
            api.register(accessToken, DeviceRegistration(pushToken, zoneName(zone()), settings.lead.value))
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    /** "Z" isn't a zone name servers accept; fixed offsets stay as "+08:00". */
    private fun zoneName(zone: ZoneId): String = if (zone.normalized() == java.time.ZoneOffset.UTC) "UTC" else zone.id
}

/** The API's names for reminder settings. */
fun ReminderLead.apiName(): String = when (this) {
    ReminderLead.OFF -> "off"
    ReminderLead.AT_START -> "at_start"
    ReminderLead.MINUTES_15 -> "15_min"
    ReminderLead.HOUR_1 -> "1_hour"
    ReminderLead.DAY_1 -> "1_day"
}
