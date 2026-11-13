package noel.chronova.feature.account.presentation

import noel.chronova.core.domain.reminders.ReminderSettings
import noel.chronova.core.model.ReminderLead
import noel.chronova.feature.account.domain.SetReminderLead
import noel.chronova.core.domain.appearance.AppearanceRepository
import noel.chronova.core.model.ThemeMode
import noel.chronova.feature.account.domain.SetThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import noel.chronova.feature.account.domain.AccountUseCases
import noel.chronova.core.domain.auth.AuthOutcome
import noel.chronova.core.domain.auth.AuthProblem
import noel.chronova.core.domain.auth.AuthRepository
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.core.domain.sync.AccountSync
import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.domain.sync.SyncStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import noel.chronova.feature.account.domain.SignIn
import noel.chronova.feature.account.domain.SignUp
import noel.chronova.feature.account.domain.ConfirmSignUp
import noel.chronova.feature.account.domain.ResendCode
import noel.chronova.feature.account.domain.RequestPasswordReset
import noel.chronova.feature.account.domain.ResetPassword
import noel.chronova.feature.account.domain.SyncAccountNow
import noel.chronova.feature.account.domain.SignOutAndClear
import noel.chronova.feature.account.domain.DeleteAccount

/** Scripted auth: each call returns the next queued outcome and is recorded. */
private class FakeAuth : AuthRepository {
    override val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val calls = mutableListOf<String>()
    val outcomes = ArrayDeque<AuthOutcome>()
    private fun next(call: String): AuthOutcome {
        calls += call
        return outcomes.removeFirstOrNull() ?: AuthOutcome.Done
    }

    override suspend fun refresh() = Unit
    override suspend fun signUp(email: String, password: String) = next("signUp:$email")
    override suspend fun confirmSignUp(email: String, code: String) = next("confirm:$email:$code").also {
        if (it == AuthOutcome.Done) state.value = AuthState.SignedIn("sub", email)
    }
    override suspend fun resendCode(email: String) = next("resend:$email")
    override suspend fun signIn(email: String, password: String, remember: Boolean) = next("signIn:$email${if (remember) "" else ":once"}").also {
        if (it == AuthOutcome.Done) state.value = AuthState.SignedIn("sub", email)
    }
    override suspend fun requestPasswordReset(email: String) = next("forgot:$email")
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = next("reset:$email:$code").also {
        if (it == AuthOutcome.Done) state.value = AuthState.SignedIn("sub", email)
    }
    override suspend fun signOut() {
        calls += "signOut"
        state.value = AuthState.SignedOut
    }
    override suspend fun accessToken(): String? = null
}

private class FakeAccountSync : AccountSync {
    override val pendingCount = MutableStateFlow(0)
    override val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    var signOuts = 0
    var deletes = 0
    var deleteResult: SyncFailure? = null
    override fun lastSyncedAt(): Long? = null
    override fun syncNow() = Unit
    override suspend fun signOutAndClear() {
        signOuts++
    }
    override suspend fun deleteAccount(): SyncFailure? {
        deletes++
        return deleteResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private lateinit var auth: FakeAuth
    private lateinit var viewModel: AccountViewModel
    private lateinit var sync: FakeAccountSync

    private val appearance = TestAppearance()
    private val reminders = TestReminders()

    @Test
    fun theReminderChoiceIsReadAndSaved() {
        assertEquals(ReminderLead.OFF, viewModel.reminderLead.value)
        viewModel.setReminderLead(ReminderLead.MINUTES_15)
        assertEquals(ReminderLead.MINUTES_15, reminders.lead.value)
        assertEquals(ReminderLead.MINUTES_15, viewModel.reminderLead.value)
    }

    @Test
    fun theThemeChoiceIsReadAndSaved() {
        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
        viewModel.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, appearance.themeMode.value)
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        auth = FakeAuth()
        sync = FakeAccountSync()
        viewModel = AccountViewModel(auth, sync, accountUseCases(auth, sync, appearance, reminders), appearance, reminders)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun rememberMeIsOnByDefaultAndItsChoiceReachesSignIn() {
        assertTrue(viewModel.form.value.remember)
        viewModel.onEmailChange("ana@example.com")
        viewModel.onPasswordChange("Long-enough-1")
        viewModel.submit()
        assertEquals("signIn:ana@example.com", auth.calls.last())

        auth.state.value = AuthState.SignedOut
        viewModel.onEmailChange("ana@example.com")
        viewModel.onPasswordChange("Long-enough-1")
        viewModel.onRememberChange(false)
        viewModel.submit()
        assertEquals("signIn:ana@example.com:once", auth.calls.last())
    }

    @Test
    fun formProblemsAreCaughtBeforeCallingCognito() {
        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("whatever")
        viewModel.submit()
        assertEquals(FormProblem.INVALID_EMAIL, viewModel.form.value.formProblem)

        viewModel.setMode(AccountMode.SIGN_UP)
        viewModel.onEmailChange("ana@example.com")
        viewModel.onPasswordChange("short")
        viewModel.submit()
        assertEquals(FormProblem.SHORT_PASSWORD, viewModel.form.value.formProblem)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun signUpThenConfirmSignsIn() {
        viewModel.setMode(AccountMode.SIGN_UP)
        viewModel.onEmailChange(" ana@example.com ")
        viewModel.onPasswordChange("Correct-horse-1")
        auth.outcomes += AuthOutcome.NeedsConfirmation
        viewModel.submit()

        val form = viewModel.form.value
        assertEquals(AccountMode.CONFIRM, form.mode)
        assertEquals("", form.password) // not kept once used
        assertEquals(AccountNotice.CODE_SENT, form.notice)

        viewModel.onCodeChange("12a34 56")
        assertEquals("123456", viewModel.form.value.code)
        viewModel.submit()
        assertEquals(AccountAuthUi.SignedIn("ana@example.com"), viewModel.authState.value)
        assertEquals(listOf("signUp:ana@example.com", "confirm:ana@example.com:123456"), auth.calls)
        assertEquals(AccountForm(), viewModel.form.value)
    }

    @Test
    fun forgotThenResetSignsIn() {
        viewModel.setMode(AccountMode.FORGOT)
        viewModel.onEmailChange("ana@example.com")
        viewModel.submit()
        assertEquals(AccountMode.RESET, viewModel.form.value.mode)
        assertEquals(AccountNotice.RESET_CODE_SENT, viewModel.form.value.notice)

        viewModel.onCodeChange("654321")
        viewModel.onPasswordChange("short")
        viewModel.submit()
        assertEquals(FormProblem.SHORT_PASSWORD, viewModel.form.value.formProblem)

        viewModel.onPasswordChange("New-password-42")
        viewModel.submit()
        assertEquals(listOf("forgot:ana@example.com", "reset:ana@example.com:654321"), auth.calls)
        assertEquals(AccountAuthUi.SignedIn("ana@example.com"), viewModel.authState.value)
    }

    @Test
    fun failedSignInShowsTheProblemAndKeepsTheForm() {
        viewModel.onEmailChange("ana@example.com")
        viewModel.onPasswordChange("wrong")
        auth.outcomes += AuthOutcome.Failed(AuthProblem.WRONG_CREDENTIALS)
        viewModel.submit()
        val form = viewModel.form.value
        assertEquals(AuthProblemUi.WRONG_CREDENTIALS, form.authProblem)
        assertEquals(false, form.busy)
        // Typing clears the message.
        viewModel.onPasswordChange("wrong2")
        assertNull(viewModel.form.value.authProblem)
    }

    @Test
    fun unconfirmedSignInMovesToTheCodeStep() {
        viewModel.onEmailChange("ana@example.com")
        viewModel.onPasswordChange("Correct-horse-1")
        auth.outcomes += AuthOutcome.NeedsConfirmation
        viewModel.submit()
        assertEquals(AccountMode.CONFIRM, viewModel.form.value.mode)

        auth.outcomes += AuthOutcome.NeedsConfirmation
        viewModel.resendCode()
        assertEquals("resend:ana@example.com", auth.calls.last())
        assertEquals(AccountNotice.CODE_SENT, viewModel.form.value.notice)
    }

    @Test
    fun signOutIsConfirmedAndWarnsAboutUnsyncedChanges() {
        auth.state.value = AuthState.SignedIn("sub", "ana@example.com")
        sync.pendingCount.value = 3
        viewModel.requestSignOut()
        assertEquals(AccountDialog.SignOut(unsynced = 3), viewModel.dialog.value)
        viewModel.dismissDialog()
        assertEquals(0, sync.signOuts)

        viewModel.requestSignOut()
        viewModel.confirmDialog()
        assertEquals(1, sync.signOuts)
        assertNull(viewModel.dialog.value)
    }

    @Test
    fun deleteAccountReportsFailures() {
        sync.deleteResult = SyncFailure.OFFLINE
        viewModel.requestDeleteAccount()
        viewModel.confirmDialog()
        assertEquals(SyncFailure.OFFLINE, viewModel.deleteFailure.value)

        sync.deleteResult = null
        viewModel.requestDeleteAccount()
        assertNull(viewModel.deleteFailure.value) // cleared when asking again
        viewModel.confirmDialog()
        assertNull(viewModel.deleteFailure.value)
        assertEquals(2, sync.deletes)
    }
}

/** The use cases the app wires, over fakes. */
private fun accountUseCases(auth: AuthRepository, sync: AccountSync, appearance: AppearanceRepository = TestAppearance(), reminders: ReminderSettings = TestReminders()) = AccountUseCases(
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
    setReminderLead = SetReminderLead(reminders),
)

/** In-memory reminder choice. */
private class TestReminders : ReminderSettings {
    override val lead = MutableStateFlow(ReminderLead.OFF)

    override fun setLead(lead: ReminderLead) {
        this.lead.value = lead
    }
}

/** In-memory appearance choice. */
private class TestAppearance : AppearanceRepository {
    override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    override fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }
}

