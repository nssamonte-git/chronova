package noel.chronova.app.rotation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import noel.chronova.feature.account.domain.AccountUseCases
import noel.chronova.core.domain.auth.AuthOutcome
import noel.chronova.core.domain.auth.AuthRepository
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.core.domain.sync.AccountSync
import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.domain.sync.SyncStatus
import noel.chronova.feature.assistant.domain.PreviewItem
import noel.chronova.feature.assistant.domain.ScheduleWarning
import noel.chronova.feature.account.presentation.AccountScreen
import noel.chronova.feature.account.presentation.AccountViewModel
import noel.chronova.feature.assistant.presentation.AssistantAvailability
import noel.chronova.feature.assistant.presentation.AssistantOverlay
import noel.chronova.feature.assistant.presentation.AssistantUiState
import noel.chronova.feature.assistant.presentation.orb.OrbMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import noel.chronova.feature.account.domain.SignIn
import noel.chronova.feature.account.domain.SignUp
import noel.chronova.feature.account.domain.ConfirmSignUp
import noel.chronova.feature.account.domain.ResendCode
import noel.chronova.feature.account.domain.RequestPasswordReset
import noel.chronova.feature.account.domain.ResetPassword
import noel.chronova.feature.account.domain.SyncAccountNow
import noel.chronova.feature.account.domain.SignOutAndClear
import noel.chronova.feature.account.domain.DeleteAccount

/**
 * What a rotation does to the screen's own state and layout, without the real app's data:
 * composables are hosted alone, with fakes behind them.
 */
@RunWith(AndroidJUnit4::class)
class RotationStateTest {
    @get:Rule
    val compose = createComposeRule()

    /** The assistant mid-answer, with the long words of a three-move offer. */
    private val talking = AssistantUiState(
        availability = AssistantAvailability.READY,
        open = true,
        mode = OrbMode.Speaking,
        heard = "Add a task called Lunch from 12 to 1 p.m., and a task called Sync from 11 to 11:30 a.m.",
        saying = "Lunch at noon and Sync at 11 today. Lunch would clash with Review and test, and Sync with Stand Up. " +
            "Shall I move Review to 9:30, Stand Up to 10:30, and test to 1 p.m.?",
        // Enough lines under the orb to fill the captions to their height limit.
        proposal = listOf(PreviewItem.Linked("Design", "Lunch"), PreviewItem.Linked("Lunch", "Sync"), PreviewItem.Linked("Sync", "Review")),
        warnings = listOf(ScheduleWarning.Overlap("Lunch", "Review and test"), ScheduleWarning.Overlap("Sync", "Stand Up")),
        offer = listOf(PreviewItem.Linked("Review", "Stand Up"), PreviewItem.Linked("Stand Up", "test"), PreviewItem.Linked("test", "Lunch")),
    )

    private fun overlayFits(width: Dp, height: Dp) {
        // The talking orb animates without end, so the screen is never idle: step the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.requiredSize(width, height).testTag("screen")) {
                AssistantOverlay(state = talking, zone = ZoneOffset.UTC, level = { 0f }, onClose = {})
            }
        }
        compose.mainClock.advanceTimeBy(1_000) // past the overlay's entrance
        val screen = compose.onNodeWithTag("screen").getUnclippedBoundsInRoot()
        // Unmerged: the tap-to-close area merges everything inside it into one node.
        val orb = compose.onNodeWithContentDescription("Chronova assistant, speaking", useUnmergedTree = true)
        val hint = compose.onNodeWithText("Tap outside the orb to stop", useUnmergedTree = true)
        for ((name, node) in listOf("orb" to orb, "hint" to hint)) {
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$name $bounds inside the screen $screen", bounds.top >= screen.top && bounds.bottom <= screen.bottom)
        }
        // Squeezed, rather than pushed off: the hint has to keep the room it needs to be read.
        val hintHeight = hint.getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("hint squeezed to $hintHeight", hintHeight >= 14.dp)
    }

    // A 720x1600 phone at density 2 (360x800 dp), less the status and navigation bars.
    @Test
    fun theTalkingAssistantFitsALandscapeScreen() = overlayFits(width = 760.dp, height = 330.dp)

    @Test
    fun theTalkingAssistantFitsAPortraitScreen() = overlayFits(width = 360.dp, height = 700.dp)

    @Test
    fun theSignInFormKeepsWhatWasTypedAndChosenAcrossARotation() {
        val auth = SignedOutAuth()
        val sync = IdleSync()
        val viewModel = AccountViewModel(auth, sync, accountUseCases(auth, sync))
        val restorer = StateRestorationTester(compose)
        restorer.setContent { AccountScreen(viewModel = viewModel, onBack = null) }

        compose.onNode(hasText("Email") and hasSetTextAction()).performTextInput("ana@example.com")
        compose.onNodeWithContentDescription("Show password").performClick()
        compose.onNodeWithText("Remember me").performClick()

        restorer.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("ana@example.com").assertExists()
        compose.onNodeWithContentDescription("Hide password").assertExists() // still shown
        compose.onNodeWithText("Remember me").assertIsOff()
    }
}

private class SignedOutAuth : AuthRepository {
    override val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override suspend fun refresh() = Unit
    override suspend fun signUp(email: String, password: String) = AuthOutcome.Done
    override suspend fun confirmSignUp(email: String, code: String) = AuthOutcome.Done
    override suspend fun resendCode(email: String) = AuthOutcome.Done
    override suspend fun signIn(email: String, password: String, remember: Boolean) = AuthOutcome.Done
    override suspend fun requestPasswordReset(email: String) = AuthOutcome.Done
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = AuthOutcome.Done
    override suspend fun signOut() = Unit
    override suspend fun accessToken(): String? = null
}

private class IdleSync : AccountSync {
    override val pendingCount: StateFlow<Int> = MutableStateFlow(0)
    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle)
    override fun lastSyncedAt(): Long? = null
    override fun syncNow() = Unit
    override suspend fun signOutAndClear() = Unit
    override suspend fun deleteAccount(): SyncFailure? = null
}

/** The use cases the app wires, over fakes. */
private fun accountUseCases(auth: AuthRepository, sync: AccountSync) = AccountUseCases(
    signIn = SignIn(auth),
    signUp = SignUp(auth),
    confirmSignUp = ConfirmSignUp(auth),
    resendCode = ResendCode(auth),
    requestPasswordReset = RequestPasswordReset(auth),
    resetPassword = ResetPassword(auth),
    syncNow = SyncAccountNow(sync),
    signOutAndClear = SignOutAndClear(sync),
    deleteAccount = DeleteAccount(sync),
)
