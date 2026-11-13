package noel.chronova.feature.sync.presentation

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.domain.sync.SyncPrompt
import noel.chronova.core.testing.FakeAccountSync
import noel.chronova.core.testing.FakeAuthRepository
import noel.chronova.feature.sync.domain.DismissSyncPrompt
import noel.chronova.feature.sync.domain.ObserveSyncOverview
import noel.chronova.feature.sync.domain.SyncNow
import noel.chronova.feature.sync.domain.SyncService
import noel.chronova.feature.sync.domain.SyncUseCases
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import noel.chronova.core.ui.R as CoreUiR

/**
 * The "back online" prompt, rendered on a device (this module's own test app) from the real
 * [SyncViewModel] over fakes, the way the app shows it.
 */
@RunWith(AndroidJUnit4::class)
class SyncPromptDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private class FakeSyncService : FakeAccountSync(), SyncService {
        override val prompt = MutableStateFlow<SyncPrompt?>(null)
        override fun offerChangesFromOtherDevice() = Unit
        override fun dismissPrompt() {
            calls += "dismissPrompt"
            prompt.value = null
        }
    }

    private val sync = FakeSyncService()
    private val viewModel = SyncViewModel(
        sync,
        SyncUseCases(
            observeOverview = ObserveSyncOverview(FakeAuthRepository(), sync),
            syncNow = SyncNow(sync),
            dismissPrompt = DismissSyncPrompt(sync),
        ),
    )
    private var signIns = 0

    private fun show(pending: Int, signedIn: Boolean) {
        sync.prompt.value = SyncPrompt(pendingChanges = pending, signedIn = signedIn)
        compose.setContent {
            ChronovaTheme {
                val prompt by viewModel.prompt.collectAsState()
                prompt?.let {
                    SyncPromptDialog(it, onSync = viewModel::syncNow, onSignIn = { signIns++ }, onLater = viewModel::dismissPrompt)
                }
            }
        }
    }

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    @Test
    fun signedInAsksToSyncTheChanges() {
        show(pending = 3, signedIn = true)
        compose.onNodeWithText(res.getString(R.string.sync_prompt_title)).assertIsDisplayed()
        compose.onNodeWithText(res.getQuantityString(R.plurals.sync_prompt_body, 3, 3)).assertIsDisplayed()
        button(res.getString(CoreUiR.string.sync_now)).assertIsDisplayed()
        button(res.getString(R.string.sync_later)).assertIsDisplayed()
        button(res.getString(R.string.sync_sign_in)).assertDoesNotExist()
    }

    @Test
    fun signedInSyncNowStartsASync() {
        show(pending = 1, signedIn = true)
        compose.onNodeWithText(res.getQuantityString(R.plurals.sync_prompt_body, 1, 1)).assertIsDisplayed()
        button(res.getString(CoreUiR.string.sync_now)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("syncNow"), sync.calls)
        assertEquals(0, signIns)
    }

    @Test
    fun signedOutAsksToSignIn() {
        show(pending = 2, signedIn = false)
        compose.onNodeWithText(res.getString(R.string.sync_prompt_title)).assertIsDisplayed()
        compose.onNodeWithText(res.getQuantityString(R.plurals.sync_prompt_body_signed_out, 2, 2)).assertIsDisplayed()
        button(res.getString(CoreUiR.string.sync_now)).assertDoesNotExist()
        button(res.getString(R.string.sync_sign_in)).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(1, signIns)
        assertEquals(emptyList<String>(), sync.calls)
    }

    @Test
    fun laterDismissesThePromptWithoutSyncing() {
        show(pending = 4, signedIn = false)
        button(res.getString(R.string.sync_later)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(res.getString(R.string.sync_prompt_title)).assertDoesNotExist()
        assertEquals(listOf("dismissPrompt"), sync.calls)
        assertEquals(0, signIns)
    }

    @Test
    fun singularAndPluralWordingDiffer() {
        // One change and several changes use their own sentence, not just a different number.
        assertEquals(false, res.getQuantityString(R.plurals.sync_prompt_body, 1, 1).replace("1", "") ==
            res.getQuantityString(R.plurals.sync_prompt_body, 5, 5).replace("5", ""))
        show(pending = 5, signedIn = true)
        compose.onNodeWithText(res.getQuantityString(R.plurals.sync_prompt_body, 5, 5)).assertIsDisplayed()
    }
}
