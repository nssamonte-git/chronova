package noel.chronova.feature.assistant.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import noel.chronova.core.domain.auth.AuthRepository
import noel.chronova.core.domain.auth.AuthState

// Whether the assistant can be used, and its session: availability, the server check, the access token, ending the session, the one-time notice and the first-visit introduction.

/** Whether the assistant can be used right now. */
enum class VoiceAvailability {
    /** This build has no server: the assistant isn't offered at all. */
    NOT_CONFIGURED,
    SIGNED_OUT,
    OFFLINE,
    READY,
}

class ObserveVoiceAvailability(private val auth: AuthRepository, private val online: Flow<Boolean>) {
    operator fun invoke(): Flow<VoiceAvailability> = combine(auth.state, online) { auth, online ->
        when {
            auth is AuthState.NotConfigured -> VoiceAvailability.NOT_CONFIGURED
            auth !is AuthState.SignedIn -> VoiceAvailability.SIGNED_OUT
            !online -> VoiceAvailability.OFFLINE
            else -> VoiceAvailability.READY
        }
    }
}

/** Whether the server can be reached right now; any failure counts as no. */
class CheckVoiceServer(private val voiceApi: VoiceApi) {
    suspend operator fun invoke(): Boolean = try {
        voiceApi.reachable()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
}

/** The voice endpoints' access token; no session counts as the server refusing it. */
class VoiceAccessToken(private val auth: AuthRepository) {
    suspend operator fun invoke(): String = auth.accessToken() ?: throw VoiceApiException(VoiceApiException.Kind.UNAUTHORIZED)
}

/** The server ended the session (a refused token): the app goes back to the sign-in page. */
class EndVoiceSession(private val auth: AuthRepository) {
    suspend operator fun invoke() = auth.endSession()
}

/** The one-time notice about what goes to ElevenLabs and OpenAI. */
class VoiceNotice(private val settings: AssistantSettings) {
    val accepted: Boolean get() = settings.noticeAccepted

    fun accept() {
        settings.noticeAccepted = true
    }
}

/** Whether the assistant has introduced itself in a project (it does so on the first open). */
class ProjectIntroduction(private val settings: AssistantSettings) {
    fun isDone(projectId: String): Boolean = settings.introduced(projectId)
    fun markDone(projectId: String) = settings.markIntroduced(projectId)
}
