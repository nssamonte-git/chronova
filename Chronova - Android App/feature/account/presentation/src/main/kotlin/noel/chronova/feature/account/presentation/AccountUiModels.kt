package noel.chronova.feature.account.presentation

import noel.chronova.core.model.ReminderLead
import noel.chronova.core.model.ThemeMode
import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.ui.sync.SyncPhase

/** Where sign-in stands, as the account screen shows it. */
sealed interface AccountAuthUi {
    /** This build has no sync backend; the app is offline-only. */
    data object NotConfigured : AccountAuthUi

    /** Restoring the saved session. */
    data object Checking : AccountAuthUi
    data object SignedOut : AccountAuthUi
    data class SignedIn(val email: String) : AccountAuthUi
}

/** Why the server turned the form down, as the form words it. */
enum class AuthProblemUi {
    WRONG_CREDENTIALS,
    ACCOUNT_EXISTS,
    WEAK_PASSWORD,
    WRONG_CODE,
    ACCOUNT_DISABLED,
    CODE_EXPIRED,
    TOO_MANY_ATTEMPTS,
    NETWORK,
    UNKNOWN,
}

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
    val authProblem: AuthProblemUi? = null,
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

/** Everything the account screen shows. */
data class AccountPageUi(
    val auth: AccountAuthUi = AccountAuthUi.SignedOut,
    val form: AccountForm = AccountForm(),
    val dialog: AccountDialog? = null,
    /** The server ended the last session; the sign-in page says so. */
    val sessionEnded: Boolean = false,
    val pending: Int = 0,
    val syncPhase: SyncPhase = SyncPhase.Idle,
    /** Why the last account deletion failed, if it did. */
    val deleteFailure: SyncFailure? = null,
    val lastSyncedAt: Long? = null,
    /** System, light or dark on this device. */
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    /** When task reminders come on this device. */
    val reminderLead: ReminderLead = ReminderLead.OFF,
)

/** What each control on the account screen does. */
data class AccountActions(
    val onEmailChange: (String) -> Unit = {},
    val onCodeChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onRememberChange: (Boolean) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onSetMode: (AccountMode) -> Unit = {},
    val onResendCode: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onRequestSignOut: () -> Unit = {},
    val onRequestDeleteAccount: () -> Unit = {},
    val onConfirmDialog: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onThemeModeChange: (ThemeMode) -> Unit = {},
    val onReminderLeadChange: (ReminderLead) -> Unit = {},
)
