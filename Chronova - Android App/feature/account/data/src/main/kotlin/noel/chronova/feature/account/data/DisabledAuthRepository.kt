package noel.chronova.feature.account.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import noel.chronova.core.domain.auth.AuthOutcome
import noel.chronova.core.domain.auth.AuthProblem
import noel.chronova.core.domain.auth.AuthRepository
import noel.chronova.core.domain.auth.AuthState

/** For builds without a backend: always [AuthState.NotConfigured]. */
class DisabledAuthRepository : AuthRepository {
    override val state: StateFlow<AuthState> = MutableStateFlow(AuthState.NotConfigured)
    override suspend fun refresh() = Unit
    override suspend fun signUp(email: String, password: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun confirmSignUp(email: String, code: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun resendCode(email: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun signIn(email: String, password: String, remember: Boolean) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun requestPasswordReset(email: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = AuthOutcome.Failed(AuthProblem.UNKNOWN)
    override suspend fun signOut() = Unit
    override suspend fun accessToken(): String? = null
}
