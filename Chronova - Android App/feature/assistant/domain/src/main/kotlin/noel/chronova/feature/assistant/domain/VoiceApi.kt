package noel.chronova.feature.assistant.domain

import java.io.File
import java.time.LocalDate

/** The server's voice endpoints (`docs/voice-api.md`). Provider keys never reach the app. */
interface VoiceApi {
    /**
     * Sends a recording (AAC in MPEG-4) and returns what was said; empty text when nothing was.
     * [keyterms] (the project's task titles) help the transcriber hear them right.
     */
    suspend fun transcribe(accessToken: String, audio: File, durationMs: Long, language: String?, keyterms: List<String>): Transcript

    /** Words (and, when speech worked, audio) saying what [request] is about. */
    suspend fun explain(accessToken: String, request: ExplainRequest): SpokenAnswer

    /** Asks the server to interpret [transcript]; cancelling the coroutine cancels the request. */
    suspend fun interpret(accessToken: String, transcript: String, context: VoiceContext): Proposal

    /** The same, from the list of projects: no project is open, so only actions (where to go) can come back. */
    suspend fun interpretOnList(accessToken: String, transcript: String, context: ListContext): Proposal

    /**
     * Asks for a clash-free alternative to [planned], the operations proposed for
     * [transcript], which would cause [clashes]; "unsupported" when there is none.
     */
    suspend fun resolve(
        accessToken: String,
        transcript: String,
        context: VoiceContext,
        planned: List<ProposedOperation>,
        clashes: List<ScheduleWarning>,
    ): Proposal

    /** Tells the server how the user answered, so the interpreter can adapt to them. */
    suspend fun decide(accessToken: String, decision: VoiceDecision)

    /** Whether the server answers at all (its health check; no account data is sent). */
    suspend fun reachable(): Boolean = true
}

/** How the user answered a proposal (`POST /v1/voice/decision`). */
data class VoiceDecision(
    /** The request as interpreted (with earlier words when it was a correction or an answer). */
    val said: String,
    val proposed: List<ExplainedChange>,
    val answer: DecisionAnswer,
    /** The clash-free alternative they were offered, if any. */
    val offered: List<ExplainedChange> = emptyList(),
    /** Their words, when they asked for something else instead. */
    val reply: String? = null,
)

enum class DecisionAnswer {
    Accepted,
    Declined,
    Corrected,

    /** Took the clash-free alternative. */
    AcceptedFix,

    /** Turned the alternative down and kept their own version. */
    KeptAsIs,
}

/** What was heard, and the language it was heard in (null if the server didn't say). */
data class Transcript(val text: String, val language: String?)

sealed interface ExplainRequest {
    val language: String?

    /** Changes to describe: as a proposal that asks to apply them, or as already applied. */
    data class Changes(
        val today: LocalDate,
        val changes: List<ExplainedChange>,
        override val language: String?,
        val stage: ExplainStage = ExplainStage.Proposal,
        /** Clashes the proposal would cause; the words name them and ask whether to apply it anyway. */
        val warnings: List<ScheduleWarning> = emptyList(),
        /** A clash-free alternative (all its changes); the question then offers it instead. */
        val offer: List<ExplainedChange> = emptyList(),
    ) : ExplainRequest

    /** A clarifying question, spoken as it is. */
    data class Question(val text: String, override val language: String?) : ExplainRequest
}

enum class ExplainStage { Proposal, Applied }

/** [audio] is null when the server could write the words but not say them. */
class SpokenAnswer(val text: String, val audio: ByteArray?, val audioType: String?)

class VoiceApiException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind {
        UNAUTHORIZED,

        /** Switched off on the server. */
        DISABLED,

        /** Too many requests; wait a minute. */
        RATE_LIMITED,

        /** The provider failed or answered with something unusable. */
        UNAVAILABLE,

        /** The request was refused as malformed (a bug). */
        REJECTED,

        /** More was said than one command can hold. */
        TOO_LONG,
        NETWORK,
    }
}
