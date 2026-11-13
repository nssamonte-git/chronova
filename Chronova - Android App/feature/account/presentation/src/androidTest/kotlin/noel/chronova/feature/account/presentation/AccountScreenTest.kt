package noel.chronova.feature.account.presentation

import noel.chronova.core.model.ThemeMode
import noel.chronova.core.testing.FakeAppearanceRepository
import noel.chronova.feature.account.domain.SetThemeMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.domain.auth.AuthOutcome
import noel.chronova.core.domain.auth.AuthProblem
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.domain.sync.SyncStatus
import noel.chronova.core.testing.FakeAccountSync
import noel.chronova.core.testing.FakeAuthRepository
import noel.chronova.feature.account.domain.AccountUseCases
import noel.chronova.feature.account.domain.ConfirmSignUp
import noel.chronova.feature.account.domain.DeleteAccount
import noel.chronova.feature.account.domain.RequestPasswordReset
import noel.chronova.feature.account.domain.ResendCode
import noel.chronova.feature.account.domain.ResetPassword
import noel.chronova.feature.account.domain.SignIn
import noel.chronova.feature.account.domain.SignOutAndClear
import noel.chronova.feature.account.domain.SignUp
import noel.chronova.feature.account.domain.SyncAccountNow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import noel.chronova.core.ui.R as CoreUiR

/**
 * The account page, rendered on a device (this module's own test app) from the real
 * [AccountViewModel] over fakes. Only visible text and semantics are checked; nothing reaches a server.
 */
@RunWith(AndroidJUnit4::class)
class AccountScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private lateinit var auth: FakeAuthRepository
    private val sync = FakeAccountSync()
    private val appearance = FakeAppearanceRepository()

    private fun show(state: AuthState) {
        auth = FakeAuthRepository(state)
        val viewModel = AccountViewModel(
            auth,
            sync,
            AccountUseCases(
                signIn = SignIn(auth),
                signUp = SignUp(auth),
                confirmSignUp = ConfirmSignUp(auth),
                resendCode = ResendCode(auth),
                requestPasswordReset = RequestPasswordReset(auth),
                resetPassword = ResetPassword(auth),
                syncNow = SyncAccountNow(sync),
                signOutAndClear = SignOutAndClear(sync),
                deleteAccount = DeleteAccount(sync),
                setThemeMode = SetThemeMode(appearance),
            ),
            appearance,
        )
        compose.setContent { ChronovaTheme { AccountScreen(viewModel, onBack = {}) } }
    }

    private fun string(id: Int) = res.getString(id)
    private fun field(label: Int) = compose.onNode(hasSetTextAction() and hasText(string(label)))
    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction() and !hasAnyAncestor(isDialog()))
    private fun dialogButton(text: String) = compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog()))
    private fun submit(title: Int) = button(string(title)).performScrollTo().performClick()

    private val passwordProblem get() = res.getQuantityString(
        R.plurals.account_problem_password, AccountViewModel.MIN_PASSWORD, AccountViewModel.MIN_PASSWORD,
    )

    // Signed out: the sign-in form.

    @Test
    fun signInRefusesAnInvalidEmail() {
        show(AuthState.SignedOut)
        compose.onNodeWithText(string(R.string.account_why)).assertIsDisplayed()
        field(R.string.account_email).performTextInput("not-an-email")
        field(R.string.account_password).performTextInput("whatever-1")
        submit(R.string.account_sign_in)

        compose.onNodeWithText(string(R.string.account_problem_email)).assertIsDisplayed()
        assertEquals(emptyList<String>(), auth.calls)

        // Fixing the email clears the message.
        field(R.string.account_email).performTextInput("@example.com")
        compose.onNodeWithText(string(R.string.account_problem_email)).assertDoesNotExist()
    }

    @Test
    fun signInAsksForAPassword() {
        show(AuthState.SignedOut)
        field(R.string.account_email).performTextInput("ana@example.com")
        submit(R.string.account_sign_in)
        compose.onNodeWithText(passwordProblem).assertIsDisplayed()
        assertEquals(emptyList<String>(), auth.calls)
    }

    @Test
    fun signInShowsTheServersAnswer() {
        show(AuthState.SignedOut)
        auth.outcome = AuthOutcome.Failed(AuthProblem.WRONG_CREDENTIALS)
        field(R.string.account_email).performTextInput("ana@example.com")
        field(R.string.account_password).performTextInput("wrong-pass-1")
        submit(R.string.account_sign_in)

        compose.onNodeWithText(string(R.string.auth_wrong_credentials)).assertIsDisplayed()
        assertEquals(listOf("signIn ana@example.com remember=true"), auth.calls)
    }

    @Test
    fun createAccountThenConfirmTheCode() {
        show(AuthState.SignedOut)
        button(string(R.string.account_no_account)).performScrollTo().performClick()
        compose.onNodeWithText(
            res.getQuantityString(R.plurals.account_password_rules, AccountViewModel.MIN_PASSWORD, AccountViewModel.MIN_PASSWORD),
        ).assertIsDisplayed()

        // Too short for a new account.
        field(R.string.account_email).performTextInput("ana@example.com")
        field(R.string.account_password).performTextInput("Short1")
        submit(R.string.account_create)
        compose.onNodeWithText(passwordProblem).assertIsDisplayed()
        assertEquals(emptyList<String>(), auth.calls)

        // The server asks for the emailed code.
        auth.outcome = AuthOutcome.NeedsConfirmation
        field(R.string.account_password).performTextInput("-Longer-pw")
        submit(R.string.account_create)
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists(string(R.string.account_confirm_body)) }
        compose.onNodeWithText(string(R.string.account_code_sent)).assertIsDisplayed()
        field(R.string.account_password).assertDoesNotExist()
        // The email is kept but can't be changed any more (a disabled field has no SetText action).
        compose.onNode(hasText(string(R.string.account_email)) and hasText("ana@example.com")).assertIsNotEnabled()
        assertEquals(listOf("signUp ana@example.com"), auth.calls)

        // A short code is caught before asking the server.
        field(R.string.account_code).performTextInput("123")
        submit(R.string.account_confirm)
        compose.onNodeWithText(
            res.getQuantityString(R.plurals.account_problem_code, AccountViewModel.CODE_LENGTH, AccountViewModel.CODE_LENGTH),
        ).assertIsDisplayed()

        // The full code confirms the account.
        auth.outcome = AuthOutcome.Done
        field(R.string.account_code).performTextInput("456")
        submit(R.string.account_confirm)
        compose.waitUntil(5_000) { auth.calls.size == 2 }
        assertEquals("confirm ana@example.com 123456", auth.calls.last())
    }

    @Test
    fun confirmCanSendANewCode() {
        show(AuthState.SignedOut)
        button(string(R.string.account_no_account)).performScrollTo().performClick()
        auth.outcome = AuthOutcome.NeedsConfirmation
        field(R.string.account_email).performTextInput("ana@example.com")
        field(R.string.account_password).performTextInput("Long-enough-1")
        submit(R.string.account_create)
        button(string(R.string.account_resend)).performScrollTo().performClick()
        compose.waitUntil(5_000) { auth.calls.size == 2 }
        assertEquals("resend ana@example.com", auth.calls.last())
        compose.onNodeWithText(string(R.string.account_code_sent)).assertIsDisplayed()
    }

    // Signed in: the account and its sync line.

    @Test
    fun theThemeChoiceIsSavedForThisDevice() {
        show(AuthState.SignedIn("user-1", "ana@example.com"))
        compose.onNodeWithText(string(R.string.account_theme)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.theme_dark)).performClick()
        compose.waitForIdle()
        assertEquals(ThemeMode.DARK, appearance.themeMode.value)
    }

    @Test
    fun signedInShowsTheAccountAndWhenItLastSynced() {
        show(AuthState.SignedIn("user-1", "ana@example.com"))
        compose.onNodeWithText(res.getString(R.string.account_signed_in_as, "ana@example.com")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.account_signed_in_body)).assertIsDisplayed()
        // Nothing pending and never synced.
        compose.onNodeWithText(string(R.string.sync_never)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.account_sign_in)).assertDoesNotExist()
    }

    @Test
    fun signedInSyncLineFollowsTheSyncState() {
        show(AuthState.SignedIn("user-1", "ana@example.com"))
        compose.runOnIdle { sync.pendingCount.value = 2 }
        compose.onNodeWithText(res.getQuantityString(CoreUiR.plurals.sync_status_pending, 2, 2)).assertIsDisplayed()

        compose.runOnIdle { sync.status.value = SyncStatus.Failed(SyncFailure.OFFLINE) }
        compose.onNodeWithText(string(CoreUiR.string.sync_failed_offline)).assertIsDisplayed()

        compose.runOnIdle { sync.status.value = SyncStatus.Syncing }
        compose.onNodeWithText(string(CoreUiR.string.sync_status_syncing)).assertIsDisplayed()
        button(string(CoreUiR.string.sync_now)).assertIsNotEnabled()
    }

    @Test
    fun syncNowSyncs() {
        show(AuthState.SignedIn("user-1", "ana@example.com"))
        button(string(CoreUiR.string.sync_now)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("syncNow"), sync.calls)
    }

    @Test
    fun signOutAsksFirstAndCancelKeepsTheAccount() {
        show(AuthState.SignedIn("user-1", "ana@example.com"))
        button(string(R.string.account_sign_out)).performClick()
        compose.onNodeWithText(string(R.string.account_sign_out_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.account_sign_out_body)).assertIsDisplayed()

        dialogButton(string(CoreUiR.string.cancel)).performClick()
        compose.onNodeWithText(string(R.string.account_sign_out_title)).assertDoesNotExist()
        assertEquals(emptyList<String>(), sync.calls)
    }

    @Test
    fun signOutWarnsAboutUnsyncedChangesAndSignsOutOnConfirm() {
        sync.pendingCount.value = 3
        show(AuthState.SignedIn("user-1", "ana@example.com"))
        button(string(R.string.account_sign_out)).performClick()
        compose.onNodeWithText(res.getQuantityString(R.plurals.account_sign_out_unsynced, 3, 3)).assertIsDisplayed()

        dialogButton(string(R.string.account_sign_out)).performClick()
        compose.waitUntil(5_000) { sync.calls.isNotEmpty() }
        assertEquals(listOf("signOutAndClear"), sync.calls)
        compose.onNodeWithText(string(R.string.account_sign_out_title)).assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextExists(text: String) =
        onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
