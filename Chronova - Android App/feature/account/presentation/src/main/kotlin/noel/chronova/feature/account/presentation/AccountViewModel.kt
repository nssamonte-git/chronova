package noel.chronova.feature.account.presentation

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
import noel.chronova.feature.account.domain.AccountUseCases
import noel.chronova.core.domain.auth.AuthOutcome
import noel.chronova.core.domain.auth.AuthProblem
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.domain.sync.SyncStatus

/** SIGN_IN/SIGN_UP → CONFIRM (code) for new accounts; FORGOT (email) → RESET (code + new password). */
enum class AccountMode { SIGN_IN, SIGN_UP, CONFIRM, FORGOT, RESET }

/** Problems the form itself catches before calling the server. */
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
    /** "Remember me" on the sign-in page: stay signed in on later launches. */
    val remember: Boolean = true,
    val busy: Boolean = false,
    val formProblem: FormProblem? = null,
    val authProblem: AuthProblem? = null,
    /** Shown after a code is (re)sent or an account is confirmed. */
    val notice: AccountNotice? = null,
)

enum class AccountNotice { CODE_SENT, RESET_CODE_SENT }

/** A confirmation the signed-in account screen is asking for. */
sealed interface AccountDialog {
    /** Signing out removes this device's data; [unsynced] changes would be lost. */
    data class SignOut(val unsynced: Int) : AccountDialog
    data object DeleteAccount : AccountDialog
}

class AccountViewModel(private val useCases: AccountUseCases) : ViewModel() {

    val authState: StateFlow<AuthState> = useCases.observeAuthState()

    /** The server ended the last session; the sign-in page says so. */

    val sessionEnded: StateFlow<Boolean> = useCases.observeSessionEnded()
    val pendingCount: StateFlow<Int> = useCases.observePendingChanges()
    val syncStatus: StateFlow<SyncStatus> = useCases.observeSyncStatus()

    private val _dialog = MutableStateFlow<AccountDialog?>(null)
    val dialog: StateFlow<AccountDialog?> = _dialog.asStateFlow()

    /** Why the last account deletion failed, if it did. */
    private val _deleteFailure = MutableStateFlow<SyncFailure?>(null)
    val deleteFailure: StateFlow<SyncFailure?> = _deleteFailure.asStateFlow()

    fun lastSyncedAt(): Long? = useCases.lastSyncedAt()

    fun syncNow() = useCases.syncNow()

    private val _form = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = _form.asStateFlow()

    fun setMode(mode: AccountMode) = _form.update { it.copy(mode = mode, password = "", code = "", formProblem = null, authProblem = null, notice = null) }

    fun onEmailChange(email: String) = _form.update { it.copy(email = email.trim(), formProblem = null, authProblem = null) }

    fun onPasswordChange(password: String) = _form.update { it.copy(password = password, formProblem = null, authProblem = null) }

    fun onRememberChange(remember: Boolean) = _form.update { it.copy(remember = remember) }

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
                AccountMode.SIGN_IN -> useCases.signIn(form.email, form.password, form.remember)
                AccountMode.SIGN_UP -> useCases.signUp(form.email, form.password)
                AccountMode.CONFIRM -> useCases.confirmSignUp(form.email, form.code)
                AccountMode.FORGOT -> useCases.requestPasswordReset(form.email)
                AccountMode.RESET -> useCases.resetPassword(form.email, form.code, form.password)
            }
            _form.value = next(form, outcome)
        }
    }

    fun resendCode() {
        val form = _form.value.takeUnless { it.busy || it.mode != AccountMode.CONFIRM } ?: return
        _form.value = form.copy(busy = true)
        viewModelScope.launch {
            val outcome = useCases.resendCode(form.email)
            _form.update {
                it.copy(
                    busy = false,
                    notice = AccountNotice.CODE_SENT.takeIf { outcome == AuthOutcome.NeedsConfirmation },
                    authProblem = (outcome as? AuthOutcome.Failed)?.problem,
                )
            }
        }
    }

    /** Always confirms: signing out also removes this device's copy of the data. */
    fun requestSignOut() {
        _dialog.value = AccountDialog.SignOut(pendingCount.value)
    }

    fun requestDeleteAccount() {
        _deleteFailure.value = null
        _dialog.value = AccountDialog.DeleteAccount
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    fun confirmDialog() {
        val dialog = _dialog.value ?: return
        _dialog.value = null
        viewModelScope.launch {
            when (dialog) {
                is AccountDialog.SignOut -> useCases.signOutAndClear()
                AccountDialog.DeleteAccount -> _deleteFailure.value = useCases.deleteAccount()
            }
            _form.value = AccountForm()
        }
    }

    private fun next(form: AccountForm, outcome: AuthOutcome): AccountForm = when (outcome) {
        AuthOutcome.Done -> when (form.mode) {
            // The reset code is on its way: ask for it and the new password.
            AccountMode.FORGOT -> AccountForm(mode = AccountMode.RESET, email = form.email, notice = AccountNotice.RESET_CODE_SENT)
            // Signed in (sign-in, confirmation or reset): the auth state changes and the form resets.
            else -> AccountForm()
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
        AccountMode.FORGOT -> if (EMAIL.matches(form.email)) null else FormProblem.INVALID_EMAIL
        AccountMode.RESET -> when {
            form.code.length != CODE_LENGTH -> FormProblem.MISSING_CODE
            form.password.length < MIN_PASSWORD -> FormProblem.SHORT_PASSWORD
            else -> null
        }
    }

    companion object {
        /** Minimum password length; the server enforces the same rule. */
        const val MIN_PASSWORD = 10
        const val CODE_LENGTH = 6
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun factory(useCases: () -> AccountUseCases): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountViewModel(useCases()) }
        }
    }
}
