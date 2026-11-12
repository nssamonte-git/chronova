package noel.chronova.app.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import noel.chronova.app.domain.auth.AuthOutcome
import noel.chronova.app.domain.auth.AuthRepository
import noel.chronova.app.domain.auth.AuthState
import noel.chronova.app.data.remote.SyncApiException
import noel.chronova.app.domain.notifications.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import noel.chronova.app.domain.sync.SyncStatus
import noel.chronova.app.domain.sync.SyncFailure
import noel.chronova.app.domain.sync.SyncPrompt

private class StubAuth(initial: AuthState) : AuthRepository {
    override val state = MutableStateFlow(initial)
    var signedOut = false
    override suspend fun refresh() = Unit
    override suspend fun signUp(email: String, password: String) = AuthOutcome.Done
    override suspend fun confirmSignUp(email: String, code: String) = AuthOutcome.Done
    override suspend fun resendCode(email: String) = AuthOutcome.Done
    override suspend fun signIn(email: String, password: String, remember: Boolean) = AuthOutcome.Done
    override suspend fun requestPasswordReset(email: String) = AuthOutcome.Done
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = AuthOutcome.Done
    override suspend fun signOut() {
        signedOut = true
        state.value = AuthState.SignedOut
    }
    override suspend fun accessToken(): String? = if (state.value is AuthState.SignedIn) "token" else null
    var ended = false
    override suspend fun endSession() {
        ended = true
        state.value = AuthState.SignedOut
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {
    private val online = MutableStateFlow(true)
    private val local = FakeLocalStore()
    private val server = FakeServer()
    private val outcomes = ArrayDeque<SyncOutcome>()
    private var runs = 0

    /**
     * The coordinator lives in backgroundScope (like the app scope), and
     * advanceUntilIdle() ignores background work, so time is advanced explicitly.
     */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private lateinit var auth: StubAuth
    private val recorded = mutableListOf<Pair<NotificationKind, Map<String, String>>>()

    private fun TestScope.coordinator(state: AuthState = AuthState.SignedIn("sub", "a@b.c")) = SyncCoordinator(
        scope = backgroundScope,
        auth = StubAuth(state).also { auth = it },
        local = local,
        api = server,
        online = online,
        runSync = {
            runs++
            outcomes.removeFirstOrNull() ?: SyncOutcome.Synced(0, 0)
        },
        clock = { 1L },
        busyRetryDelayMillis = 10,
        notify = { kind, args -> recorded += kind to args },
    )

    @Test
    fun promptsOnlyOnAnOfflineToOnlineTransitionWithPendingChanges() = runTest(StandardTestDispatcher()) {
        val sync = coordinator()
        local.edit(task("t1", 1))
        settle()
        assertNull("no prompt at start-up", sync.prompt.value)

        online.value = false
        settle()
        online.value = true
        settle()
        assertEquals(SyncPrompt(pendingChanges = 1, signedIn = true), sync.prompt.value)
        assertEquals("sync never starts on its own", 0, runs)
    }

    @Test
    fun noPromptWithoutPendingChangesOrWithoutABackend() = runTest(StandardTestDispatcher()) {
        val sync = coordinator()
        online.value = false; settle(); online.value = true; settle()
        assertNull(sync.prompt.value)

        val offlineOnly = coordinator(AuthState.NotConfigured)
        local.edit(task("t1", 1))
        online.value = false; settle(); online.value = true; settle()
        assertNull(offlineOnly.prompt.value)
    }

    @Test
    fun signedOutPromptOffersSignIn() = runTest(StandardTestDispatcher()) {
        val sync = coordinator(AuthState.SignedOut)
        local.edit(task("t1", 1))
        online.value = false; settle(); online.value = true; settle()
        assertEquals(SyncPrompt(1, signedIn = false), sync.prompt.value)
    }

    @Test
    fun laterDismissesUntilTheNextReconnect() = runTest(StandardTestDispatcher()) {
        val sync = coordinator()
        local.edit(task("t1", 1))
        online.value = false; settle(); online.value = true; settle()
        sync.dismissPrompt()
        assertNull(sync.prompt.value)
        online.value = false; settle(); online.value = true; settle()
        assertEquals(1, sync.prompt.value?.pendingChanges)
    }

    @Test
    fun syncNowRetriesWhenBusyThenReportsTheResult() = runTest(StandardTestDispatcher()) {
        val sync = coordinator()
        outcomes += SyncOutcome.Failed(SyncApiException.Kind.BUSY)
        outcomes += SyncOutcome.Synced(pushed = 2, pulled = 3)
        sync.syncNow()
        settle()
        assertEquals(2, runs)
        assertEquals(SyncStatus.Synced(at = 1L, pushed = 2, pulled = 3), sync.status.value)
    }

    @Test
    fun failuresAreMappedForTheUser() = runTest(StandardTestDispatcher()) {
        val sync = coordinator()
        // (A rejected token ends the session instead: aRejectedTokenEndsTheSessionButKeepsTheDevicesData.)
        outcomes += SyncOutcome.Failed(SyncApiException.Kind.SERVER)
        sync.syncNow(); settle()
        assertEquals(SyncStatus.Failed(SyncFailure.SERVER), sync.status.value)

        val signedOut = coordinator(AuthState.SignedOut)
        signedOut.syncNow(); settle()
        assertEquals(SyncStatus.Failed(SyncFailure.NOT_SIGNED_IN), signedOut.status.value)
    }

    @Test
    fun deleteAccountClearsTheDeviceOnlyAfterTheServerSucceeds() = runTest(StandardTestDispatcher()) {
        val sync = coordinator()
        local.edit(task("t1", 1))
        server.failNext = SyncApiException.Kind.NETWORK
        assertEquals(SyncFailure.OFFLINE, sync.deleteAccount())
        assertEquals(false, local.cleared)

        assertNull(sync.deleteAccount())
        assertTrue(server.deletedAccount)
        assertTrue(local.cleared)
    }

    @Test
    fun aRejectedTokenEndsTheSessionButKeepsTheDevicesData() = runTest(StandardTestDispatcher()) {
        val sync = coordinator()
        local.edit(task("t1", 1))
        outcomes.addLast(SyncOutcome.Failed(SyncApiException.Kind.UNAUTHORIZED))
        sync.syncNow()
        settle()
        assertTrue(auth.ended)
        assertEquals(AuthState.SignedOut, auth.state.value)
        assertEquals("no stale failure for whoever signs in next", SyncStatus.Idle, sync.status.value)
        assertFalse(local.cleared) // the same account finds its unsynced work again
        assertEquals(1, local.dirtyChanges().size)
    }

    @Test
    fun otherFailuresKeepTheSession() = runTest(StandardTestDispatcher()) {
        val sync = coordinator()
        outcomes.addLast(SyncOutcome.Failed(SyncApiException.Kind.NETWORK))
        sync.syncNow()
        settle()
        assertFalse(auth.ended)
    }

    @Test
    fun recordsSyncsFailuresReplacedChangesAndWaitingChanges() = runTest(StandardTestDispatcher()) {
        val sync = coordinator()
        outcomes.addLast(SyncOutcome.Synced(pushed = 3, pulled = 2, replaced = 1))
        outcomes.addLast(SyncOutcome.Failed(SyncApiException.Kind.NETWORK))
        sync.syncNow()
        settle()
        sync.syncNow()
        settle()
        local.edit(task("t1", 1))
        online.value = false
        settle()
        online.value = true
        settle()

        assertEquals(
            listOf(
                NotificationKind.SYNCED to mapOf("sent" to "3", "received" to "2"),
                NotificationKind.SYNC_CONFLICT to mapOf("count" to "1"),
                NotificationKind.SYNC_FAILED to mapOf("reason" to "OFFLINE"),
                NotificationKind.SYNC_WAITING to mapOf("count" to "1"),
            ),
            recorded,
        )
    }

    @Test
    fun anEndedSessionIsRecordedBySignInNotHere() = runTest(StandardTestDispatcher()) {
        val sync = coordinator()
        outcomes.addLast(SyncOutcome.Failed(SyncApiException.Kind.UNAUTHORIZED))
        sync.syncNow()
        settle()
        assertTrue(recorded.isEmpty())
    }
}
