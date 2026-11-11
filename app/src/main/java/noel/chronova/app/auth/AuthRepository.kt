package noel.chronova.app.auth

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
    suspend fun signIn(email: String, password: String): AuthOutcome

    /** Emails a reset code (the answer doesn't reveal whether the account exists). */
    suspend fun requestPasswordReset(email: String): AuthOutcome

    /** Sets a new password with the emailed code, signs out other devices and signs in here. */
    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthOutcome

    suspend fun signOut()

    /** A valid access token for the sync API (refreshed if needed), or null when signed out. */
    suspend fun accessToken(): String?
}

/** For builds without a backend: always [AuthState.NotConfigured]. */
class DisabledAuthRepository : AuthRepository {
    override val state: StateFlow<AuthState> = kotlinx.coroutines.flow.MutableStateFlow(AuthState.NotConfigured)
    override suspend fun refresh() = Unit
    override suspend fun signUp(email: String, password: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun confirmSignUp(email: String, code: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun resendCode(email: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun signIn(email: String, password: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun requestPasswordReset(email: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun signOut() = Unit
    override suspend fun accessToken(): String? = null
}
