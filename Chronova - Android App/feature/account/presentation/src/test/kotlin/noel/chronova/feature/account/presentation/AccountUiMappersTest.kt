package noel.chronova.feature.account.presentation

import noel.chronova.core.domain.auth.AuthProblem
import noel.chronova.core.domain.auth.AuthState
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountUiMappersTest {
    @Test
    fun authStateKeepsOnlyWhatTheScreenShows() {
        assertEquals(AccountAuthUi.NotConfigured, AuthState.NotConfigured.toUi())
        assertEquals(AccountAuthUi.Checking, AuthState.Checking.toUi())
        assertEquals(AccountAuthUi.SignedOut, AuthState.SignedOut.toUi())
        assertEquals(AccountAuthUi.SignedIn("ana@example.com"), AuthState.SignedIn(userId = "sub", email = "ana@example.com").toUi())
    }

    @Test
    fun theDemoAccountIsMarkedWhateverTheCase() {
        assertEquals(AccountAuthUi.SignedIn("test@chronova.com", isDemo = true), AuthState.SignedIn("sub", "test@chronova.com").toUi())
        assertEquals(AccountAuthUi.SignedIn("Test@Chronova.com", isDemo = true), AuthState.SignedIn("sub", "Test@Chronova.com").toUi())
        assertEquals(AccountAuthUi.SignedIn("test@chronova.co", isDemo = false), AuthState.SignedIn("sub", "test@chronova.co").toUi())
    }

    @Test
    fun everyAuthProblemHasItsOwnWording() {
        assertEquals(AuthProblem.entries.map { it.name }, AuthProblem.entries.map { it.toUi().name })
    }
}
