package noel.chronova.app.auth

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class MemoryTokenStore : TokenStore {
    var session: Session? = null
    override fun read() = session
    override fun write(session: Session) {
        this.session = session
    }
    override fun clear() {
        session = null
    }
}

class ApiAuthRepositoryTest {
    private val server = MockWebServer()
    private val store = MemoryTokenStore()
    private lateinit var auth: ApiAuthRepository

    private val signedInBody = """{"token":"1|abc","user":{"id":"u-1","email":"ana@example.com"}}"""

    @Before
    fun setUp() {
        server.start()
        auth = ApiAuthRepository(server.url("/").toString(), OkHttpClient(), store)
    }

    @After
    fun tearDown() = server.close()

    private fun reply(code: Int, body: String = "") = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun signUpPostsLowerCasedEmailAndAsksForTheCode() = runTest {
        reply(202, """{"status":"verification_required"}""")
        assertEquals(AuthOutcome.NeedsConfirmation, auth.signUp(" Ana@Example.com ", "Correct-horse-1"))
        val request = server.takeRequest()
        assertEquals("/v1/auth/register", request.url.encodedPath)
        val body = request.body!!.utf8()
        assertTrue(body, body.contains(""""email":"ana@example.com"""") && body.contains(""""password":"Correct-horse-1""""))
    }

    @Test
    fun confirmingSignsInAndStoresTheSession() = runTest {
        reply(200, signedInBody)
        assertEquals(AuthOutcome.Done, auth.confirmSignUp("ana@example.com", "123456"))
        assertEquals(Session("1|abc", "u-1", "ana@example.com"), store.session)
        assertEquals(AuthState.SignedIn("u-1", "ana@example.com"), auth.state.value)
        assertEquals("1|abc", auth.accessToken())
    }

    @Test
    fun serverErrorsMapToProblems() = runTest {
        reply(401, """{"error":"invalid_credentials"}""")
        assertEquals(AuthOutcome.Failed(AuthProblem.WRONG_CREDENTIALS), auth.signIn("a@b.c", "x"))
        reply(403, """{"error":"verification_required"}""")
        assertEquals(AuthOutcome.NeedsConfirmation, auth.signIn("a@b.c", "x"))
        reply(403, """{"error":"account_disabled"}""")
        assertEquals(AuthOutcome.Failed(AuthProblem.ACCOUNT_DISABLED), auth.signIn("a@b.c", "x"))
        reply(422, """{"error":"invalid_code"}""")
        assertEquals(AuthOutcome.Failed(AuthProblem.WRONG_CODE), auth.confirmSignUp("a@b.c", "000000"))
        reply(422, """{"message":"…","errors":{"password":["too weak"]}}""")
        assertEquals(AuthOutcome.Failed(AuthProblem.WEAK_PASSWORD), auth.signUp("a@b.c", "weak"))
        reply(429)
        assertEquals(AuthOutcome.Failed(AuthProblem.TOO_MANY_ATTEMPTS), auth.signIn("a@b.c", "x"))
        assertNull(store.session)
    }

    @Test
    fun unreachableServerIsANetworkProblem() = runTest {
        server.close()
        assertEquals(AuthOutcome.Failed(AuthProblem.NETWORK), auth.signIn("a@b.c", "x"))
    }

    @Test
    fun resetPasswordFlowSignsIn() = runTest {
        reply(202, """{"status":"reset_code_sent"}""")
        assertEquals(AuthOutcome.Done, auth.requestPasswordReset("ana@example.com"))
        reply(200, signedInBody)
        assertEquals(AuthOutcome.Done, auth.resetPassword("ana@example.com", "123456", "New-password-42"))
        server.takeRequest()
        assertEquals("/v1/auth/reset", server.takeRequest().url.encodedPath)
        assertEquals("u-1", (auth.state.value as AuthState.SignedIn).userId)
    }

    @Test
    fun signOutRevokesTheTokenAndForgetsItEvenIfTheServerFails() = runTest {
        store.session = Session("1|abc", "u-1", "ana@example.com")
        auth.refresh()
        reply(500)
        auth.signOut()
        val request = server.takeRequest()
        assertEquals("/v1/auth/logout", request.url.encodedPath)
        assertEquals("Bearer 1|abc", request.headers["Authorization"])
        assertNull(store.session)
        assertEquals(AuthState.SignedOut, auth.state.value)
    }

    @Test
    fun refreshRestoresASavedSession() = runTest {
        auth.refresh()
        assertEquals(AuthState.SignedOut, auth.state.value)
        store.session = Session("1|abc", "u-1", "ana@example.com")
        auth.refresh()
        assertEquals(AuthState.SignedIn("u-1", "ana@example.com"), auth.state.value)
    }
}
