package noel.chronova.app.navigation

import noel.chronova.app.domain.auth.AuthState

/**
 * What the app shows at its root. Using Chronova always needs an account: signed out, only
 * the sign-in page (with sign-up and password reset) is reachable. A build with no server
 * configured can't sign anyone in, so it never gets past that page.
 */
enum class AppGate {
    /** The saved session is being restored. */
    Loading,
    SignIn,
    App,
    ;

    companion object {
        fun of(state: AuthState): AppGate = when (state) {
            AuthState.Checking -> Loading
            AuthState.SignedOut, AuthState.NotConfigured -> SignIn
            is AuthState.SignedIn -> App
        }
    }
}
