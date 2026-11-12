package noel.chronova.app.domain.auth

import kotlinx.coroutines.flow.StateFlow

sealed interface AuthState {
    /** This build has no sync backend; the app is offline-only. */
    data object NotConfigured : AuthState

    /** Restoring the saved session. */
    data object Checking : AuthState
    data object SignedOut : AuthState

    /** [userId] is the Cognito `sub`, the owner of the user's synced data. */
    data class SignedIn(val userId: String, val email: String) : AuthState
}

sealed interface AuthOutcome {
    data object Done : AuthOutcome

    /** The account exists but its email isn't confirmed yet; a code was (re)sent. */
    data object NeedsConfirmation : AuthOutcome
    data class Failed(val problem: AuthProblem) : AuthOutcome
}

enum class AuthProblem {
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

/** Sign-in for sync. Passwords go straight to the server and are never stored by the app. */
interface AuthRepository {
    val state: StateFlow<AuthState>

    /** Re-reads the saved session (e.g. at start-up). */
    suspend fun refresh()

    suspend fun signUp(email: String, password: String): AuthOutcome

    /** Confirms the email with the emailed code and signs in. */
    suspend fun confirmSignUp(email: String, code: String): AuthOutcome
    suspend fun resendCode(email: String): AuthOutcome
    /** [remember]: stay signed in on later launches ("Remember me"); otherwise only while the app runs. */
    suspend fun signIn(email: String, password: String, remember: Boolean = true): AuthOutcome

    /** Emails a reset code (the answer doesn't reveal whether the account exists). */
    suspend fun requestPasswordReset(email: String): AuthOutcome

    /** Sets a new password with the emailed code, signs out other devices and signs in here. */
    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthOutcome

    suspend fun signOut()

    /**
     * The server no longer accepts this device's token (it expired after 60 unused days,
     * staff signed the user out everywhere, or the account was disabled): forget it and
     * return to the sign-in page. Unlike [signOut], the device's data stays, for the same
     * account to find again (the auth implementation's `beforeSignIn` clears it for another).
     */
    suspend fun endSession() {}

    /** True after [endSession], until the next sign-in, so the sign-in page can say why. */
    val sessionEnded: StateFlow<Boolean> get() = NEVER_ENDED

    /** A valid access token for the sync API (refreshed if needed), or null when signed out. */
    suspend fun accessToken(): String?

    private companion object {
        val NEVER_ENDED: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false)
    }
}
