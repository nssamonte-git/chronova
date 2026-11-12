package noel.chronova.feature.sync.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.feature.sync.domain.DismissSyncPrompt
import noel.chronova.feature.sync.domain.ObserveSyncOverview
import noel.chronova.feature.sync.domain.SyncNow
import noel.chronova.core.domain.sync.SyncPrompt
import noel.chronova.feature.sync.domain.SyncService
import noel.chronova.core.domain.sync.SyncStatus
import noel.chronova.feature.sync.domain.SyncUseCases
import noel.chronova.core.testing.FakeAccountSync
import noel.chronova.core.testing.FakeAuthRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import noel.chronova.core.ui.sync.SyncUi

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {
    private class FakeSyncService : FakeAccountSync(), SyncService {
        override val prompt = MutableStateFlow<SyncPrompt?>(null)
        override fun dismissPrompt() {
            calls += "dismissPrompt"
            prompt.value = null
        }
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val auth = FakeAuthRepository()
    private val sync = FakeSyncService()
    private lateinit var viewModel: SyncViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SyncViewModel(
            sync,
            SyncUseCases(
                observeOverview = ObserveSyncOverview(auth, sync),
                syncNow = SyncNow(sync),
                dismissPrompt = DismissSyncPrompt(sync),
            ),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theSyncLineShowsTheAccountAndPendingChanges() = runTest(dispatcher) {
        val collecting = backgroundScope.launch { viewModel.ui.collect {} }
        sync.pendingCount.value = 2
        sync.status.value = SyncStatus.Syncing
        assertEquals(SyncUi(configured = true, signedIn = true, pending = 2, status = SyncStatus.Syncing), viewModel.ui.value)

        auth.state.value = AuthState.NotConfigured
        assertEquals(false, viewModel.ui.value.configured)
        collecting.cancel()
    }

    @Test
    fun thePromptCanBeAnsweredEitherWay() {
        sync.prompt.value = SyncPrompt(pendingChanges = 1, signedIn = true)
        assertEquals(1, viewModel.prompt.value?.pendingChanges)
        viewModel.syncNow()
        viewModel.dismissPrompt()
        assertEquals(null, viewModel.prompt.value)
        assertEquals(listOf("syncNow", "dismissPrompt"), sync.calls)
    }
}
