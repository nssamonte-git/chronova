package noel.chronova.app.domain.account

import kotlinx.coroutines.flow.StateFlow
import noel.chronova.app.domain.auth.AuthOutcome
import noel.chronova.app.domain.auth.AuthRepository
import noel.chronova.app.domain.auth.AuthState
import noel.chronova.app.domain.sync.AccountSync
import noel.chronova.app.domain.sync.SyncFailure
import noel.chronova.app.domain.sync.SyncStatus

class ObserveAuthState(private val auth: AuthRepository) {
    operator fun invoke(): StateFlow<AuthState> = auth.state
}

/** True after the server ended the session, until the next sign-in, so the sign-in page can say why. */
class ObserveSessionEnded(private val auth: AuthRepository) {
    operator fun invoke(): StateFlow<Boolean> = auth.sessionEnded
}

/** [remember]: stay signed in on later launches; otherwise only while the app runs. */
class SignIn(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, remember: Boolean): AuthOutcome = auth.signIn(email, password, remember)
}

class SignUp(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AuthOutcome = auth.signUp(email, password)
}

/** Confirms the email with the emailed code and signs in. */
class ConfirmSignUp(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, code: String): AuthOutcome = auth.confirmSignUp(email, code)
}

class ResendCode(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String): AuthOutcome = auth.resendCode(email)
}

/** Emails a reset code (the answer doesn't reveal whether the account exists). */
class RequestPasswordReset(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String): AuthOutcome = auth.requestPasswordReset(email)
}

/** Sets a new password with the emailed code, signs out other devices and signs in here. */
class ResetPassword(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, code: String, newPassword: String): AuthOutcome = auth.resetPassword(email, code, newPassword)
}

/** Changes on this phone that haven't been synced yet. */
class ObservePendingChanges(private val sync: AccountSync) {
    operator fun invoke(): StateFlow<Int> = sync.pendingCount
}

class ObserveSyncStatus(private val sync: AccountSync) {
    operator fun invoke(): StateFlow<SyncStatus> = sync.status
}

/** When this account last synced on this phone, or null. */
class LastSyncedAt(private val sync: AccountSync) {
    operator fun invoke(): Long? = sync.lastSyncedAt()
}

/** "Sync now" on the account page. */
class SyncAccountNow(private val sync: AccountSync) {
    operator fun invoke() = sync.syncNow()
}

/** Signs out and clears this account's data from the phone. */
class SignOutAndClear(private val sync: AccountSync) {
    suspend operator fun invoke() = sync.signOutAndClear()
}

/** Deletes the account on the server (and its data here); null on success. */
class DeleteAccount(private val sync: AccountSync) {
    suspend operator fun invoke(): SyncFailure? = sync.deleteAccount()
}

/** Everything the sign-in pages and the account page do. */
class AccountUseCases(
    val observeAuthState: ObserveAuthState,
    val observeSessionEnded: ObserveSessionEnded,
    val signIn: SignIn,
    val signUp: SignUp,
    val confirmSignUp: ConfirmSignUp,
    val resendCode: ResendCode,
    val requestPasswordReset: RequestPasswordReset,
    val resetPassword: ResetPassword,
    val observePendingChanges: ObservePendingChanges,
    val observeSyncStatus: ObserveSyncStatus,
    val lastSyncedAt: LastSyncedAt,
    val syncNow: SyncAccountNow,
    val signOutAndClear: SignOutAndClear,
    val deleteAccount: DeleteAccount,
)
