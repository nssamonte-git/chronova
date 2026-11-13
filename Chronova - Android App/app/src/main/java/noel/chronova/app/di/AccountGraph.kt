package noel.chronova.app.di

import noel.chronova.app.AppContainer
import noel.chronova.core.domain.appearance.AppearanceQueries
import noel.chronova.core.domain.auth.AuthQueries
import noel.chronova.core.domain.sync.SyncStatusQueries
import noel.chronova.feature.account.domain.AccountUseCases
import noel.chronova.feature.account.domain.ConfirmSignUp
import noel.chronova.feature.account.domain.DeleteAccount
import noel.chronova.feature.account.domain.RequestPasswordReset
import noel.chronova.feature.account.domain.ResendCode
import noel.chronova.feature.account.domain.ResetPassword
import noel.chronova.feature.account.domain.SetThemeMode
import noel.chronova.feature.account.domain.SignIn
import noel.chronova.feature.account.domain.SignOutAndClear
import noel.chronova.feature.account.domain.SignUp
import noel.chronova.feature.account.domain.SyncAccountNow

/** The sign-in pages', the account page's and the sign-in gate's wiring. */
class AccountGraph(private val app: AppContainer) {
    /** The sign-in state, read by the sign-in gate and the account pages. */
    val auth: AuthQueries get() = app.authRepository

    /** This device's appearance (theme), read by the account page. */
    val appearance: AppearanceQueries get() = app.appearance

    /** Unsynced changes, the last result and time, read by the account page. */
    val syncStatus: SyncStatusQueries get() = app.syncCoordinator

    fun useCases(): AccountUseCases {
        val auth = app.authRepository
        val sync = app.syncCoordinator
        return AccountUseCases(
            signIn = SignIn(auth),
            signUp = SignUp(auth),
            confirmSignUp = ConfirmSignUp(auth),
            resendCode = ResendCode(auth),
            requestPasswordReset = RequestPasswordReset(auth),
            resetPassword = ResetPassword(auth),
            syncNow = SyncAccountNow(sync),
            signOutAndClear = SignOutAndClear(sync),
            deleteAccount = DeleteAccount(sync),
            setThemeMode = SetThemeMode(app.appearance),
        )
    }
}
