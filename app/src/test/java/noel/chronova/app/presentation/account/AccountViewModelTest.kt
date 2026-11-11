package noel.chronova.app.presentation.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import noel.chronova.app.auth.AuthOutcome
import noel.chronova.app.auth.AuthProblem
import noel.chronova.app.auth.AuthRepository
import noel.chronova.app.auth.AuthState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    override suspend fun confirmSignUp(email: String, code: String) = next("confirm:$email:$code")
    override suspend fun resendCode(email: String) = next("resend:$email")
    override suspend fun signIn(email: String, password: String) = next("signIn:$email").also {
        if (it == AuthOutcome.Done) state.value = AuthState.SignedIn("sub", email)
    }
    override suspend fun signOut() {
        calls += "signOut"
        state.value = AuthState.SignedOut
    }
    override suspend fun accessToken(): String? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private lateinit var auth: FakeAuth
    private lateinit var viewModel: AccountViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        auth = FakeAuth()
        viewModel = AccountViewModel(auth)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

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
    fun signUpConfirmThenSignIn() {
        viewModel.setMode(AccountMode.SIGN_UP)
        viewModel.onEmailChange(" ana@example.com ")
        viewModel.onPasswordChange("Correct-horse-1")
        auth.outcomes += AuthOutcome.NeedsConfirmation
        viewModel.submit()

        var form = viewModel.form.value
        assertEquals(AccountMode.CONFIRM, form.mode)
        assertEquals("", form.password) // not kept once used
        assertEquals(AccountNotice.CODE_SENT, form.notice)

        viewModel.onCodeChange("12a34 56")
        assertEquals("123456", viewModel.form.value.code)
        viewModel.submit()
        form = viewModel.form.value
        assertEquals(AccountMode.SIGN_IN, form.mode)
        assertEquals("ana@example.com", form.email)
        assertEquals(AccountNotice.CONFIRMED, form.notice)

        viewModel.onPasswordChange("Correct-horse-1")
        viewModel.submit()
        assertEquals(AuthState.SignedIn("sub", "ana@example.com"), viewModel.authState.value)
        assertEquals(listOf("signUp:ana@example.com", "confirm:ana@example.com:123456", "signIn:ana@example.com"), auth.calls)
        assertEquals(AccountForm(), viewModel.form.value)
    }

    @Test
    fun failedSignInShowsTheProblemAndKeepsTheForm() {
        viewModel.onEmailChange("ana@example.com")
        viewModel.onPasswordChange("wrong")
        auth.outcomes += AuthOutcome.Failed(AuthProblem.WRONG_CREDENTIALS)
        viewModel.submit()
        val form = viewModel.form.value
        assertEquals(AuthProblem.WRONG_CREDENTIALS, form.authProblem)
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
    fun signOutResetsTheForm() {
        auth.state.value = AuthState.SignedIn("sub", "ana@example.com")
        viewModel.signOut()
        assertEquals(AuthState.SignedOut, viewModel.authState.value)
        assertEquals("signOut", auth.calls.single())
    }
}
