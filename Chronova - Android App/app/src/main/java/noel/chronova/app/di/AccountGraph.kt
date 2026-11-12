package noel.chronova.app.di

import noel.chronova.app.AppContainer
import noel.chronova.feature.account.domain.AccountUseCases
import noel.chronova.feature.account.domain.ConfirmSignUp
import noel.chronova.feature.account.domain.DeleteAccount
import noel.chronova.feature.account.domain.LastSyncedAt
import noel.chronova.feature.account.domain.ObserveAuthState
import noel.chronova.feature.account.domain.ObservePendingChanges
import noel.chronova.feature.account.domain.ObserveSessionEnded
import noel.chronova.feature.account.domain.ObserveSyncStatus
import noel.chronova.feature.account.domain.RequestPasswordReset
import noel.chronova.feature.account.domain.ResendCode
import noel.chronova.feature.account.domain.ResetPassword
import noel.chronova.feature.account.domain.SignIn
import noel.chronova.feature.account.domain.SignOutAndClear
import noel.chronova.feature.account.domain.SignUp
import noel.chronova.feature.account.domain.SyncAccountNow

/** The sign-in pages', the account page's and the sign-in gate's wiring. */
class AccountGraph(private val app: AppContainer) {
    /** Signed in or not, for the app's sign-in gate. */
    val observeAuthState = ObserveAuthState(app.authRepository)

    fun useCases(): AccountUseCases {
        val auth = app.authRepository
        val sync = app.syncCoordinator
        return AccountUseCases(
            observeAuthState = observeAuthState,
            observeSessionEnded = ObserveSessionEnded(auth),
            signIn = SignIn(auth),
            signUp = SignUp(auth),
            confirmSignUp = ConfirmSignUp(auth),
            resendCode = ResendCode(auth),
            requestPasswordReset = RequestPasswordReset(auth),
            resetPassword = ResetPassword(auth),
            observePendingChanges = ObservePendingChanges(sync),
            observeSyncStatus = ObserveSyncStatus(sync),
            lastSyncedAt = LastSyncedAt(sync),
            syncNow = SyncAccountNow(sync),
            signOutAndClear = SignOutAndClear(sync),
            deleteAccount = DeleteAccount(sync),
        )
    }
}
