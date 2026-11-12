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
