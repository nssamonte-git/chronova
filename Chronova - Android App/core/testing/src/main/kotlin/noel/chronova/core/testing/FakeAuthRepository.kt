package noel.chronova.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import noel.chronova.core.domain.auth.AuthOutcome
import noel.chronova.core.domain.auth.AuthRepository
import noel.chronova.core.domain.auth.AuthState

/** An [AuthRepository] that records calls and answers with [outcome]. */
class FakeAuthRepository(initial: AuthState = AuthState.SignedIn("user-1", "a@example.com")) : AuthRepository {
    override val state = MutableStateFlow(initial)
    override val sessionEnded = MutableStateFlow(false)
    var outcome: AuthOutcome = AuthOutcome.Done
    var token: String? = "token-1"
    val calls = mutableListOf<String>()

    override suspend fun refresh() = Unit
    override suspend fun signUp(email: String, password: String) = outcome.also { calls += "signUp $email" }
    override suspend fun confirmSignUp(email: String, code: String) = outcome.also { calls += "confirm $email $code" }
    override suspend fun resendCode(email: String) = outcome.also { calls += "resend $email" }
    override suspend fun signIn(email: String, password: String, remember: Boolean) = outcome.also { calls += "signIn $email remember=$remember" }
    override suspend fun requestPasswordReset(email: String) = outcome.also { calls += "forgot $email" }
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = outcome.also { calls += "reset $email $code" }
    override suspend fun signOut() {
        calls += "signOut"
    }
    override suspend fun endSession() {
        calls += "endSession"
        sessionEnded.value = true
    }
    override suspend fun accessToken(): String? = token

    /** What [checkSession] answers; false also ends the session. */
    var sessionWorks: Boolean? = true
    override suspend fun checkSession(): Boolean? {
        calls += "checkSession"
        if (sessionWorks == false) endSession()
        return sessionWorks
    }
}
