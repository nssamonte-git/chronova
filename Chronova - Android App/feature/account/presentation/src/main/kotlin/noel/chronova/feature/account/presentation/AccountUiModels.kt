package noel.chronova.feature.account.presentation

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
