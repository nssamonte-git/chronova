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
    fun everyAuthProblemHasItsOwnWording() {
        assertEquals(AuthProblem.entries.map { it.name }, AuthProblem.entries.map { it.toUi().name })
    }
}
