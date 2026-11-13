package noel.chronova.feature.push.domain

import kotlinx.coroutines.flow.first
import noel.chronova.core.domain.auth.AuthRepository
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.domain.notifications.NotificationRepository

/**
 * Acts on the account and sync alerts (`docs/push.md`), only while signed in:
 * - changes on another device: a notification; tapping it offers to sync (sync never starts on its own);
 * - a new sign-in elsewhere: a notification, unless this phone itself signed in moments ago (the
 *   server can't tell this phone's new sign-in from another's until the phone registers again);
 * - account changed: the push alone is not trusted; the server is asked, and only a refused
 *   sign-in ends the session here (which says so itself).
 */
class HandleAlert(
    private val auth: AuthRepository,
    private val notifications: NotificationRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(message: PushMessage) {
        if (auth.state.value !is AuthState.SignedIn) return
        when (message) {
            PushMessage.SyncAvailable -> notifications.add(NotificationKind.SYNC_AVAILABLE)
            PushMessage.SignedIn -> if (!signedInHereJustNow()) notifications.add(NotificationKind.SIGNED_IN_ELSEWHERE)
            PushMessage.AccountChanged -> auth.checkSession()
            is PushMessage.Reminder -> Unit
        }
    }

    private suspend fun signedInHereJustNow(): Boolean {
        val now = clock()
        return notifications.observe().first().any {
            (it.kind == NotificationKind.SIGNED_IN || it.kind == NotificationKind.PASSWORD_CHANGED) && now - it.createdAt in 0..OWN_SIGN_IN_MILLIS
        }
    }

    companion object {
        /** A sign-in alert this soon after this phone's own sign-in is about this phone. */
        const val OWN_SIGN_IN_MILLIS = 5 * 60_000L
    }
}
