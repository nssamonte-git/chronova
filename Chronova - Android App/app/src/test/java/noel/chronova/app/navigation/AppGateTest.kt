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
    fun theAppOpensWhenSignedInOrWhenThisBuildHasNoServer() {
        assertEquals(AppGate.App, AppGate.of(AuthState.SignedIn("u1", "ana@example.com")))
        assertEquals(AppGate.App, AppGate.of(AuthState.NotConfigured))
    }

    @Test
    fun nothingIsShownWhileTheSavedSessionIsRestored() {
        assertEquals(AppGate.Loading, AppGate.of(AuthState.Checking))
    }
}
