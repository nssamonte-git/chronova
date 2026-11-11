package noel.chronova.app.auth

import android.content.Context
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.AuthUserAttribute
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.cognito.exceptions.service.CodeExpiredException
import com.amplifyframework.auth.cognito.exceptions.service.CodeMismatchException
import com.amplifyframework.auth.cognito.exceptions.service.FailedAttemptsLimitExceededException
import com.amplifyframework.auth.cognito.exceptions.service.InvalidPasswordException
import com.amplifyframework.auth.cognito.exceptions.service.LimitExceededException
import com.amplifyframework.auth.cognito.exceptions.service.TooManyRequestsException
import com.amplifyframework.auth.cognito.exceptions.service.UserNotConfirmedException
import com.amplifyframework.auth.cognito.exceptions.service.UserNotFoundException
import com.amplifyframework.auth.cognito.exceptions.service.UsernameExistsException
import com.amplifyframework.auth.exceptions.NotAuthorizedException
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.core.AmplifyConfiguration
import com.amplifyframework.kotlin.core.Amplify
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import com.amplifyframework.core.Amplify as AmplifyCore

/**
 * Cognito sign-in through Amplify's auth plugin only (no Amplify DataStore: sync is
 * our own, user-confirmed protocol). The email address is the Cognito username.
 * Amplify keeps the tokens in its own encrypted store and refreshes them.
 */
class AmplifyAuthRepository private constructor() : AuthRepository {

    private val _state = MutableStateFlow<AuthState>(AuthState.Checking)
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    override suspend fun refresh() {
        _state.value = currentState()
    }

    override suspend fun signUp(email: String, password: String): AuthOutcome = attempt {
        val options = AuthSignUpOptions.builder()
            .userAttributes(listOf(AuthUserAttribute(AuthUserAttributeKey.email(), email)))
            .build()
        val result = Amplify.Auth.signUp(email, password, options)
        if (result.isSignUpComplete) AuthOutcome.Done else AuthOutcome.NeedsConfirmation
    }

    override suspend fun confirmSignUp(email: String, code: String): AuthOutcome = attempt {
        Amplify.Auth.confirmSignUp(email, code)
        AuthOutcome.Done
    }

    override suspend fun resendCode(email: String): AuthOutcome = attempt {
        Amplify.Auth.resendSignUpCode(email)
        AuthOutcome.NeedsConfirmation
    }

    override suspend fun signIn(email: String, password: String): AuthOutcome = attempt {
        val result = Amplify.Auth.signIn(email, password)
        refresh()
        if (result.isSignedIn) AuthOutcome.Done else AuthOutcome.Failed(AuthProblem.UNKNOWN)
    }

    override suspend fun signOut() {
        // Local sign-out always clears this device's tokens, even if revoking them online fails.
        Amplify.Auth.signOut()
        _state.value = AuthState.SignedOut
    }

    override suspend fun accessToken(): String? = try {
        val session = Amplify.Auth.fetchAuthSession() as? AWSCognitoAuthSession
        session?.userPoolTokensResult?.value?.accessToken
    } catch (_: AuthException) {
        null
    }

    private suspend fun currentState(): AuthState = try {
        val session = Amplify.Auth.fetchAuthSession() as? AWSCognitoAuthSession
        val sub = session?.userSubResult?.value
        if (session?.isSignedIn == true && sub != null) {
            val email = Amplify.Auth.fetchUserAttributes()
                .firstOrNull { it.key == AuthUserAttributeKey.email() }?.value.orEmpty()
            AuthState.SignedIn(sub, email)
        } else {
            AuthState.SignedOut
        }
    } catch (_: AuthException) {
        AuthState.SignedOut
    }

    private suspend fun attempt(block: suspend () -> AuthOutcome): AuthOutcome = try {
        block()
    } catch (error: AuthException) {
        when (error) {
            is UserNotConfirmedException -> AuthOutcome.NeedsConfirmation
            else -> AuthOutcome.Failed(problemOf(error))
        }
    }

    private fun problemOf(error: AuthException): AuthProblem = when {
        error is NotAuthorizedException || error is UserNotFoundException -> AuthProblem.WRONG_CREDENTIALS
        error is UsernameExistsException -> AuthProblem.ACCOUNT_EXISTS
        error is InvalidPasswordException -> AuthProblem.WEAK_PASSWORD
        error is CodeMismatchException -> AuthProblem.WRONG_CODE
        error is CodeExpiredException -> AuthProblem.CODE_EXPIRED
        error is LimitExceededException || error is TooManyRequestsException ||
            error is FailedAttemptsLimitExceededException -> AuthProblem.TOO_MANY_ATTEMPTS
        generateSequence<Throwable>(error) { it.cause }.any { it is IOException } -> AuthProblem.NETWORK
        else -> AuthProblem.UNKNOWN
    }

    companion object {
        /**
         * Configures Amplify once per process with the user pool from [config]
         * (SRP sign-in, no identity pool) and returns the repository.
         */
        fun create(context: Context, config: BackendConfig): AmplifyAuthRepository {
            val json = JSONObject().put(
                "auth",
                JSONObject().put(
                    "plugins",
                    JSONObject().put(
                        "awsCognitoAuthPlugin",
                        JSONObject()
                            .put(
                                "CognitoUserPool",
                                JSONObject().put(
                                    "Default",
                                    JSONObject()
                                        .put("PoolId", config.userPoolId)
                                        .put("AppClientId", config.userPoolClientId)
                                        .put("Region", config.region),
                                ),
                            )
                            .put("Auth", JSONObject().put("Default", JSONObject().put("authenticationFlowType", "USER_SRP_AUTH"))),
                    ),
                ),
            )
            AmplifyCore.addPlugin(AWSCognitoAuthPlugin())
            AmplifyCore.configure(AmplifyConfiguration.fromJson(json), context.applicationContext)
            return AmplifyAuthRepository()
        }
    }
}
