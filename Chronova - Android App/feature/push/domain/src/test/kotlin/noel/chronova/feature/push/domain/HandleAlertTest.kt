package noel.chronova.feature.push.domain

import kotlinx.coroutines.test.runTest
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.testing.FakeAuthRepository
import noel.chronova.core.testing.FakeNotificationRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class HandleAlertTest {
    private val auth = FakeAuthRepository()
    private val notifications = FakeNotificationRepository()
    private var now = HandleAlert.OWN_SIGN_IN_MILLIS * 10
    private val handle = HandleAlert(auth, notifications, clock = { now })

    private fun kinds() = notifications.added.map { it.notification.kind }

    @Test
    fun changesOnAnotherDeviceAndANewSignInAreNotified() = runTest {
        handle(PushMessage.SyncAvailable)
        handle(PushMessage.SignedIn)
        assertEquals(listOf(NotificationKind.SYNC_AVAILABLE, NotificationKind.SIGNED_IN_ELSEWHERE), kinds())
    }

    @Test
    fun aSignInAlertMomentsAfterSigningInHereIsAboutThisPhone() = runTest {
        notifications.add(NotificationKind.SIGNED_IN, mapOf("email" to "a@example.com")) // created at 1
        now = 1 + HandleAlert.OWN_SIGN_IN_MILLIS
        handle(PushMessage.SignedIn)
        assertEquals(listOf(NotificationKind.SIGNED_IN), kinds())

        now += 1
        handle(PushMessage.SignedIn)
        assertEquals(listOf(NotificationKind.SIGNED_IN, NotificationKind.SIGNED_IN_ELSEWHERE), kinds())
    }

    @Test
    fun anAccountChangeIsCheckedWithTheServerBeforeAnythingEnds() = runTest {
        handle(PushMessage.AccountChanged)
        assertEquals(listOf("checkSession"), auth.calls)
        assertEquals(false, auth.sessionEnded.value)

        auth.sessionWorks = false
        handle(PushMessage.AccountChanged)
        assertEquals(listOf("checkSession", "checkSession", "endSession"), auth.calls)

        auth.calls.clear()
        auth.sessionWorks = null // offline: nothing changes
        auth.sessionEnded.value = false
        handle(PushMessage.AccountChanged)
        assertEquals(listOf("checkSession"), auth.calls)
        assertEquals(false, auth.sessionEnded.value)
        assertEquals(emptyList<NotificationKind>(), kinds())
    }

    @Test
    fun nothingWhileSignedOut() = runTest {
        auth.state.value = AuthState.SignedOut
        listOf(PushMessage.SyncAvailable, PushMessage.SignedIn, PushMessage.AccountChanged).forEach { handle(it) }
        assertEquals(emptyList<NotificationKind>(), kinds())
        assertEquals(emptyList<String>(), auth.calls)
    }
}
