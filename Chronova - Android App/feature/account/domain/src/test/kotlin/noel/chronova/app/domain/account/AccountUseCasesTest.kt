package noel.chronova.app.domain.account

import kotlinx.coroutines.test.runTest
import noel.chronova.app.domain.auth.AuthOutcome
import noel.chronova.app.domain.auth.AuthProblem
import noel.chronova.app.domain.sync.SyncFailure
import noel.chronova.app.testing.FakeAccountSync
import noel.chronova.app.testing.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountUseCasesTest {
    private val auth = FakeAuthRepository()
    private val sync = FakeAccountSync()

    @Test
    fun signingInPassesRememberMeAndReturnsTheOutcome() = runTest {
        auth.outcome = AuthOutcome.Failed(AuthProblem.WRONG_CREDENTIALS)
        assertEquals(auth.outcome, SignIn(auth)("a@example.com", "pw", remember = false))
        assertEquals(listOf("signIn a@example.com remember=false"), auth.calls)
    }

    @Test
    fun signingOutClearsThroughSyncAndDeletingReportsAFailure() = runTest {
        SignOutAndClear(sync)()
        sync.deleteFailure = SyncFailure.OFFLINE
        assertEquals(SyncFailure.OFFLINE, DeleteAccount(sync)())
        assertEquals(listOf("signOutAndClear", "deleteAccount"), sync.calls)
    }

    @Test
    fun theAccountPageReadsSyncState() {
        sync.pendingCount.value = 5
        sync.lastSynced = 1_234L
        assertEquals(5, ObservePendingChanges(sync)().value)
        assertEquals(1_234L, LastSyncedAt(sync)())
        SyncAccountNow(sync)()
        assertEquals(listOf("syncNow"), sync.calls)
    }
}
