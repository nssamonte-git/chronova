package noel.chronova.feature.account.presentation

import noel.chronova.core.domain.auth.AuthProblem
import noel.chronova.core.domain.auth.AuthState

// Domain to screen: the account screen never sees the domain's auth types.

internal fun AuthState.toUi(): AccountAuthUi = when (this) {
    AuthState.NotConfigured -> AccountAuthUi.NotConfigured
    AuthState.Checking -> AccountAuthUi.Checking
    AuthState.SignedOut -> AccountAuthUi.SignedOut
    is AuthState.SignedIn -> AccountAuthUi.SignedIn(email)
}

internal fun AuthProblem.toUi(): AuthProblemUi = when (this) {
    AuthProblem.WRONG_CREDENTIALS -> AuthProblemUi.WRONG_CREDENTIALS
    AuthProblem.ACCOUNT_EXISTS -> AuthProblemUi.ACCOUNT_EXISTS
    AuthProblem.WEAK_PASSWORD -> AuthProblemUi.WEAK_PASSWORD
    AuthProblem.WRONG_CODE -> AuthProblemUi.WRONG_CODE
    AuthProblem.ACCOUNT_DISABLED -> AuthProblemUi.ACCOUNT_DISABLED
    AuthProblem.CODE_EXPIRED -> AuthProblemUi.CODE_EXPIRED
    AuthProblem.TOO_MANY_ATTEMPTS -> AuthProblemUi.TOO_MANY_ATTEMPTS
    AuthProblem.NETWORK -> AuthProblemUi.NETWORK
    AuthProblem.UNKNOWN -> AuthProblemUi.UNKNOWN
}
