package noel.chronova.app.navigation

import noel.chronova.app.auth.AuthState

/**
 * What the app shows at its root. Using Chronova needs an account: signed out, only the
 * sign-in page (with sign-up and password reset) is reachable. A build with no server
 * configured can't sign in, so it opens the app as before.
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
            AuthState.SignedOut -> SignIn
            is AuthState.SignedIn, AuthState.NotConfigured -> App
        }
    }
}
