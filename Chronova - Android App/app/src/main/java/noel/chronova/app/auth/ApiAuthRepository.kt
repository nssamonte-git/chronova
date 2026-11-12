package noel.chronova.app.auth

import java.io.IOException
import noel.chronova.app.domain.notifications.NotificationKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import noel.chronova.app.data.remote.SyncJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import noel.chronova.app.domain.auth.AuthState
import noel.chronova.app.domain.auth.AuthOutcome
import noel.chronova.app.domain.auth.AuthProblem
import noel.chronova.app.domain.auth.AuthRepository

/**
 * Sign-in against the Chronova server's `/v1/auth` endpoints (Sanctum tokens).
 * The token is kept by [store]; the email is lower-cased like the server does.
 */
class ApiAuthRepository(
    baseUrl: String,
    private val client: OkHttpClient,
    private val store: TokenStore,
    /**
     * Runs before a session (new or restored) becomes signed in, with its user id: the
     * place to make sure the device's data belongs to that account.
     */
    private val beforeSignIn: suspend (userId: String) -> Unit = {},
    /** Records account events for the notifications page (after [beforeSignIn], which may clear them). */
    private val notify: suspend (NotificationKind, Map<String, String>) -> Unit = { _, _ -> },
) : AuthRepository {
    private val base = baseUrl.trimEnd('/')

    private val _state = MutableStateFlow<AuthState>(AuthState.Checking)
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _sessionEnded = MutableStateFlow(false)
    override val sessionEnded: StateFlow<Boolean> = _sessionEnded.asStateFlow()

    /** A session signed in without "Remember me": kept only while the app runs, never written to [store]. */
    @Volatile
    private var unsaved: Session? = null

    private suspend fun session(): Session? = withContext(Dispatchers.IO) { store.read() } ?: unsaved

    private suspend fun forget() {
        unsaved = null
        withContext(Dispatchers.IO) { store.clear() }
    }

    override suspend fun refresh() {
        val session = session()
        session?.let { beforeSignIn(it.userId) }
        _state.value = session?.let { AuthState.SignedIn(it.userId, it.email) } ?: AuthState.SignedOut
    }

    override suspend fun endSession() {
        if (_state.value !is AuthState.SignedIn) return
        forget()
        _sessionEnded.value = true
        _state.value = AuthState.SignedOut
        notify(NotificationKind.SESSION_ENDED, emptyMap())
    }

    override suspend fun signUp(email: String, password: String): AuthOutcome =
        when (val r = post("auth/register", body(email) { put("password", password) })) {
            is Reply.Ok -> AuthOutcome.NeedsConfirmation
            is Reply.Error -> failure(r)
        }

    override suspend fun confirmSignUp(email: String, code: String): AuthOutcome =
        signedIn(post("auth/verify", body(email) { put("code", code); put("device_name", DEVICE) }), NotificationKind.SIGNED_IN)

    override suspend fun resendCode(email: String): AuthOutcome =
        when (val r = post("auth/resend", body(email))) {
            is Reply.Ok -> AuthOutcome.NeedsConfirmation
            is Reply.Error -> failure(r)
        }

    override suspend fun signIn(email: String, password: String, remember: Boolean): AuthOutcome =
        signedIn(post("auth/login", body(email) { put("password", password); put("device_name", DEVICE) }), NotificationKind.SIGNED_IN, remember)

    override suspend fun requestPasswordReset(email: String): AuthOutcome =
        when (val r = post("auth/forgot", body(email))) {
            is Reply.Ok -> AuthOutcome.Done
            is Reply.Error -> failure(r)
        }

    override suspend fun resetPassword(email: String, code: String, newPassword: String): AuthOutcome =
        signedIn(post("auth/reset", body(email) { put("code", code); put("password", newPassword); put("device_name", DEVICE) }), NotificationKind.PASSWORD_CHANGED)

    override suspend fun signOut() {
        val token = session()?.token
        // Revoke the token on the server when possible; this device forgets it either way.
        if (token != null) post("auth/logout", JsonObject(emptyMap()), token)
        forget()
        _state.value = AuthState.SignedOut
    }

    override suspend fun accessToken(): String? = session()?.token

    /** [remember]: keep the session for later launches; otherwise only while the app runs. */
    private suspend fun signedIn(reply: Reply, event: NotificationKind, remember: Boolean = true): AuthOutcome = when (reply) {
        is Reply.Error -> failure(reply)
        is Reply.Ok -> {
            val json = reply.json ?: return AuthOutcome.Failed(AuthProblem.UNKNOWN)
            val user = json.getValue("user").jsonObject
            val session = Session(
                token = json.getValue("token").jsonPrimitive.content,
                userId = user.getValue("id").jsonPrimitive.content,
                email = user.getValue("email").jsonPrimitive.content,
            )
            beforeSignIn(session.userId)
            if (remember) {
                unsaved = null
                withContext(Dispatchers.IO) { store.write(session) }
            } else {
                // Nothing on disk: the next launch opens on the sign-in page.
                withContext(Dispatchers.IO) { store.clear() }
                unsaved = session
            }
            _sessionEnded.value = false
            _state.value = AuthState.SignedIn(session.userId, session.email)
            notify(event, mapOf("email" to session.email))
            AuthOutcome.Done
        }
    }

    private fun failure(reply: Reply.Error): AuthOutcome = when {
        reply.code == -1 -> AuthOutcome.Failed(AuthProblem.NETWORK)
        reply.code == 429 -> AuthOutcome.Failed(AuthProblem.TOO_MANY_ATTEMPTS)
        reply.error == "verification_required" -> AuthOutcome.NeedsConfirmation
        reply.error == "invalid_credentials" -> AuthOutcome.Failed(AuthProblem.WRONG_CREDENTIALS)
        reply.error == "account_disabled" -> AuthOutcome.Failed(AuthProblem.ACCOUNT_DISABLED)
        reply.error == "invalid_code" -> AuthOutcome.Failed(AuthProblem.WRONG_CODE)
        reply.code == 422 && "password" in reply.fieldErrors -> AuthOutcome.Failed(AuthProblem.WEAK_PASSWORD)
        else -> AuthOutcome.Failed(AuthProblem.UNKNOWN)
    }

    private fun body(email: String, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("email", email.trim().lowercase())
        extra()
    }

    private sealed interface Reply {
        data class Ok(val json: JsonObject?) : Reply

        /** [code] is -1 when the server couldn't be reached. */
        data class Error(val code: Int, val error: String?, val fieldErrors: Set<String>) : Reply
    }

    private suspend fun post(path: String, body: JsonObject, token: String? = null): Reply = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/v1/$path")
            .header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .post(SyncJson.json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                val json = runCatching { SyncJson.json.parseToJsonElement(text).jsonObject }.getOrNull()
                if (response.isSuccessful) {
                    Reply.Ok(json)
                } else {
                    Reply.Error(
                        code = response.code,
                        error = json?.get("error")?.jsonPrimitive?.content,
                        fieldErrors = json?.get("errors")?.jsonObject?.keys.orEmpty(),
                    )
                }
            }
        } catch (_: IOException) {
            Reply.Error(-1, null, emptySet())
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val DEVICE = "android"
    }
}
