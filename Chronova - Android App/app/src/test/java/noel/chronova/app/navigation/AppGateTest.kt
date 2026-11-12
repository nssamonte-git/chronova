package noel.chronova.app.navigation

import noel.chronova.app.auth.AuthState
import org.junit.Assert.assertEquals
import org.junit.Test

class AppGateTest {
    @Test
    fun signedOutOnlyReachesTheSignInPage() {
        assertEquals(AppGate.SignIn, AppGate.of(AuthState.SignedOut))
    }

    @Test
    fun aBuildWithNoServerNeverGetsPastTheSignInPage() {
        assertEquals(AppGate.SignIn, AppGate.of(AuthState.NotConfigured))
    }

    @Test
    fun theAppOpensOnlyWhenSignedIn() {
        assertEquals(AppGate.App, AppGate.of(AuthState.SignedIn("u1", "ana@example.com")))
    }

    @Test
    fun nothingIsShownWhileTheSavedSessionIsRestored() {
        assertEquals(AppGate.Loading, AppGate.of(AuthState.Checking))
    }
}
