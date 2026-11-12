package noel.chronova.app.domain.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import noel.chronova.app.domain.auth.AuthState
import noel.chronova.app.testing.FakeAccountSync
import noel.chronova.app.testing.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/** [SyncService] over the shared account-sync fake. */
class FakeSyncService : FakeAccountSync(), SyncService {
    override val prompt = MutableStateFlow<SyncPrompt?>(null)
    override fun dismissPrompt() {
        calls += "dismissPrompt"
        prompt.value = null
    }
}

class SyncUseCasesTest {
    private val auth = FakeAuthRepository()
    private val sync = FakeSyncService()

    @Test
    fun theOverviewFollowsTheAccountAndSyncState() = runTest {
        val observe = ObserveSyncOverview(auth, sync)
        sync.pendingCount.value = 3
        sync.status.value = SyncStatus.Failed(SyncFailure.OFFLINE)
        assertEquals(SyncOverview(configured = true, signedIn = true, pending = 3, status = SyncStatus.Failed(SyncFailure.OFFLINE)), observe().first())

        auth.state.value = AuthState.SignedOut
        assertEquals(false, observe().first().signedIn)
        auth.state.value = AuthState.NotConfigured
        assertEquals(false, observe().first().configured)
    }

    @Test
    fun syncingAndDismissingGoToTheService() {
        sync.prompt.value = SyncPrompt(pendingChanges = 2, signedIn = true)
        SyncNow(sync)()
        DismissSyncPrompt(sync)()
        assertEquals(listOf("syncNow", "dismissPrompt"), sync.calls)
        assertEquals(null, ObserveSyncPrompt(sync)().value)
    }
}
