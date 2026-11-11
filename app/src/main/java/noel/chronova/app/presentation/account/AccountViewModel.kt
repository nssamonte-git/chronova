package noel.chronova.app.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import noel.chronova.app.auth.AuthOutcome
import noel.chronova.app.auth.AuthProblem
import noel.chronova.app.auth.AuthRepository
import noel.chronova.app.auth.AuthState

enum class AccountMode { SIGN_IN, SIGN_UP, CONFIRM }

/** Problems the form itself catches before calling Cognito. */
enum class FormProblem { INVALID_EMAIL, SHORT_PASSWORD, MISSING_CODE }

/**
 * The sign-in form. The password lives only here, in memory: it is never saved to
 * instance state or disk, and it is cleared as soon as it has been used.
 */
data class AccountForm(
    val mode: AccountMode = AccountMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val formProblem: FormProblem? = null,
    val authProblem: AuthProblem? = null,
    /** Shown after a code is (re)sent or an account is confirmed. */
    val notice: AccountNotice? = null,
)

enum class AccountNotice { CODE_SENT, CONFIRMED }

class AccountViewModel(private val auth: AuthRepository) : ViewModel() {

    val authState: StateFlow<AuthState> = auth.state

    private val _form = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = _form.asStateFlow()

    fun setMode(mode: AccountMode) = _form.update { it.copy(mode = mode, password = "", code = "", formProblem = null, authProblem = null, notice = null) }

    fun onEmailChange(email: String) = _form.update { it.copy(email = email.trim(), formProblem = null, authProblem = null) }

    fun onPasswordChange(password: String) = _form.update { it.copy(password = password, formProblem = null, authProblem = null) }

    fun onCodeChange(code: String) = _form.update { it.copy(code = code.filter(Char::isDigit).take(CODE_LENGTH), formProblem = null, authProblem = null) }

    fun submit() {
        val form = _form.value.takeUnless { it.busy } ?: return
        val problem = validate(form)
        if (problem != null) {
            _form.value = form.copy(formProblem = problem)
            return
        }
        _form.value = form.copy(busy = true, notice = null)
        viewModelScope.launch {
            val outcome = when (form.mode) {
                AccountMode.SIGN_IN -> auth.signIn(form.email, form.password)
                AccountMode.SIGN_UP -> auth.signUp(form.email, form.password)
                AccountMode.CONFIRM -> auth.confirmSignUp(form.email, form.code)
            }
            _form.value = next(form, outcome)
        }
    }

    fun resendCode() {
        val form = _form.value.takeUnless { it.busy || it.mode != AccountMode.CONFIRM } ?: return
        _form.value = form.copy(busy = true)
        viewModelScope.launch {
            val outcome = auth.resendCode(form.email)
            _form.update {
                it.copy(
                    busy = false,
                    notice = AccountNotice.CODE_SENT.takeIf { outcome == AuthOutcome.NeedsConfirmation },
                    authProblem = (outcome as? AuthOutcome.Failed)?.problem,
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _form.value = AccountForm()
        }
    }

    private fun next(form: AccountForm, outcome: AuthOutcome): AccountForm = when (outcome) {
        // Signed in: the auth state changes; the form resets.
        AuthOutcome.Done -> when (form.mode) {
            AccountMode.SIGN_IN -> AccountForm()
            // Confirmed: ask the user to sign in with the password they chose.
            AccountMode.CONFIRM, AccountMode.SIGN_UP ->
                AccountForm(mode = AccountMode.SIGN_IN, email = form.email, notice = AccountNotice.CONFIRMED)
        }
        AuthOutcome.NeedsConfirmation ->
            form.copy(mode = AccountMode.CONFIRM, password = "", busy = false, notice = AccountNotice.CODE_SENT, authProblem = null)
        is AuthOutcome.Failed -> form.copy(busy = false, authProblem = outcome.problem, code = "")
    }

    private fun validate(form: AccountForm): FormProblem? = when (form.mode) {
        AccountMode.SIGN_IN, AccountMode.SIGN_UP -> when {
            !EMAIL.matches(form.email) -> FormProblem.INVALID_EMAIL
            form.mode == AccountMode.SIGN_UP && form.password.length < MIN_PASSWORD -> FormProblem.SHORT_PASSWORD
            form.password.isEmpty() -> FormProblem.SHORT_PASSWORD
            else -> null
        }
        AccountMode.CONFIRM -> if (form.code.length == CODE_LENGTH) null else FormProblem.MISSING_CODE
    }

    companion object {
        /** Matches the user pool's policy (backend/lib/chronova-stack.ts). */
        const val MIN_PASSWORD = 10
        const val CODE_LENGTH = 6
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun factory(auth: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountViewModel(auth) }
        }
    }
}
