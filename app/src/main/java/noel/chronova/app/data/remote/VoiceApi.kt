package noel.chronova.app.data.remote

import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import noel.chronova.app.domain.voice.ExplainedChange
import noel.chronova.app.domain.voice.Proposal
import noel.chronova.app.domain.voice.ProposedOperation
import noel.chronova.app.domain.voice.VoiceContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** The server's voice endpoints (`docs/voice-api.md`). Provider keys never reach the app. */
interface VoiceApi {
    /** Sends a recording (AAC in MPEG-4) and returns what was said; empty text when nothing was. */
    suspend fun transcribe(accessToken: String, audio: File, durationMs: Long, language: String?): Transcript

    /** Words (and, when speech worked, audio) saying what [request] is about. */
    suspend fun explain(accessToken: String, request: ExplainRequest): SpokenAnswer

    /** Asks the server to interpret [transcript]; cancelling the coroutine cancels the request. */
    suspend fun interpret(accessToken: String, transcript: String, context: VoiceContext): Proposal
}

/** What was heard, and the language it was heard in (null if the server didn't say). */
data class Transcript(val text: String, val language: String?)

sealed interface ExplainRequest {
    val language: String?

    /** The previewed changes, to be summarised before the user confirms. */
    data class Changes(val today: LocalDate, val changes: List<ExplainedChange>, override val language: String?) : ExplainRequest

    /** A clarifying question, spoken as it is. */
    data class Question(val text: String, override val language: String?) : ExplainRequest
}

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

    companion object {
        fun forStatus(code: Int): Kind = when (code) {
            401, 403 -> Kind.UNAUTHORIZED
            429 -> Kind.RATE_LIMITED
            503 -> Kind.DISABLED
            in 400..499 -> Kind.REJECTED
            else -> Kind.UNAVAILABLE
        }
    }
}

class OkHttpVoiceApi(
    baseUrl: String,
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : VoiceApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun transcribe(accessToken: String, audio: File, durationMs: Long, language: String?): Transcript {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", "command.m4a", audio.asRequestBody(M4A))
            .addFormDataPart("durationMs", durationMs.toString())
            .apply { if (language != null) addFormDataPart("language", language) }
            .build()
        val reply = call("transcribe", form, TranscribeResponseDto.serializer(), accessToken)
        return Transcript(reply.text.trim(), reply.language)
    }

    override suspend fun explain(accessToken: String, request: ExplainRequest): SpokenAnswer {
        val dto = when (request) {
            is ExplainRequest.Changes -> ExplainRequestDto(
                today = request.today.toString(),
                changes = request.changes.map { it.toDto() },
                language = request.language,
            )
            is ExplainRequest.Question -> ExplainRequestDto(question = request.text, language = request.language)
        }
        val body = SyncJson.json.encodeToString(ExplainRequestDto.serializer(), dto)
        val reply = call("explain", body.toRequestBody(JSON), ExplainResponseDto.serializer(), accessToken)
        val audio = try {
            reply.audio?.let { Base64.getDecoder().decode(it) }
        } catch (_: IllegalArgumentException) {
            null // Unreadable audio: the words are still worth showing.
        }
        return SpokenAnswer(reply.text, audio, if (audio != null) reply.audioType else null)
    }

    override suspend fun interpret(accessToken: String, transcript: String, context: VoiceContext): Proposal {
        val body = SyncJson.json.encodeToString(InterpretRequestDto.serializer(), InterpretRequestDto(transcript, context.toDto()))
        return call("interpret", body.toRequestBody(JSON), InterpretResponseDto.serializer(), accessToken).toDomain()
    }

    // Callers are often on the main thread (ViewModels): the whole call, body reading included, runs on [io].
    private suspend fun <T> call(
        endpoint: String,
        body: RequestBody,
        reply: KSerializer<T>,
        accessToken: String,
    ): T = withContext(io) {
        val request = Request.Builder()
            .url("$base/v1/voice/$endpoint")
            .post(body)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()
        val text = try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    val tooLong = response.code == 422 && TOO_LONG in response.body.string()
                    throw VoiceApiException(if (tooLong) VoiceApiException.Kind.TOO_LONG else VoiceApiException.forStatus(response.code))
                }
                response.body.string()
            }
        } catch (error: IOException) {
            throw VoiceApiException(VoiceApiException.Kind.NETWORK, error)
        }
        try {
            SyncJson.json.decodeFromString(reply, text)
        } catch (error: SerializationException) {
            throw VoiceApiException(VoiceApiException.Kind.UNAVAILABLE, error)
        } catch (error: IllegalArgumentException) {
            throw VoiceApiException(VoiceApiException.Kind.UNAVAILABLE, error)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val M4A = "audio/mp4".toMediaType()
        const val TOO_LONG = "transcript_too_long"
    }
}

@Serializable
internal data class TranscribeResponseDto(val text: String, val language: String? = null)

@Serializable
internal data class ExplainRequestDto(
    val today: String? = null,
    val changes: List<ExplainChangeDto>? = null,
    val question: String? = null,
    val language: String? = null,
)

/** Null fields are left out (`explicitNulls = false`): the server wants exactly the fields of each kind. */
@Serializable
internal data class ExplainChangeDto(
    val change: String,
    val task: String? = null,
    val title: String? = null,
    val predecessor: String? = null,
    val successor: String? = null,
    val fromStart: String? = null,
    val fromEnd: String? = null,
    val toStart: String? = null,
    val toEnd: String? = null,
)

@Serializable
internal data class ExplainResponseDto(val text: String, val audio: String? = null, val audioType: String? = null)

internal fun ExplainedChange.toDto() = ExplainChangeDto(
    change = change,
    task = task,
    title = title,
    predecessor = predecessor,
    successor = successor,
    fromStart = fromStart?.wire(),
    fromEnd = fromEnd?.wire(),
    toStart = toStart?.wire(),
    toEnd = toEnd?.wire(),
)

@Serializable
internal data class InterpretRequestDto(val transcript: String, val context: VoiceContextDto)

@Serializable
internal data class VoiceContextDto(
    val projectId: String,
    val projectName: String,
    val startDate: String,
    val endDate: String,
    val timeZone: String,
    val today: String,
    val now: String,
    val revision: String,
    val tasks: List<VoiceTaskDto>,
)

@Serializable
internal data class VoiceTaskDto(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val predecessorIds: List<String>,
)

@Serializable
internal data class InterpretResponseDto(
    val revision: String? = null,
    val result: String,
    val operations: List<OperationDto> = emptyList(),
    val question: String? = null,
    val reason: String? = null,
)

@Serializable
internal data class OperationDto(
    val type: String,
    val taskId: String? = null,
    val date: String? = null,
    val time: String? = null,
    val amount: Long? = null,
    val unit: String? = null,
    val start: String? = null,
    val end: String? = null,
    val title: String? = null,
    val predecessorIds: List<String>? = null,
    val predecessorId: String? = null,
    val successorId: String? = null,
)

/** Local date-times as the API expects them: minutes, no seconds, no offset. */
// ISO-8601 leaves out zero seconds, giving "2026-11-12T09:00".
private fun LocalDateTime.wire(): String = withSecond(0).withNano(0).toString()

internal fun VoiceContext.toDto() = VoiceContextDto(
    projectId = projectId,
    projectName = projectName,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    timeZone = timeZone,
    today = today.toString(),
    now = now.wire(),
    revision = revision,
    tasks = tasks.map { VoiceTaskDto(it.id, it.title, it.start.wire(), it.end.wire(), it.predecessorIds) },
)

internal fun InterpretResponseDto.toDomain() = Proposal(
    revision = revision,
    result = result,
    operations = operations.map {
        ProposedOperation(
            type = it.type,
            taskId = it.taskId,
            date = it.date,
            time = it.time,
            amount = it.amount,
            unit = it.unit,
            start = it.start,
            end = it.end,
            title = it.title,
            predecessorIds = it.predecessorIds,
            predecessorId = it.predecessorId,
            successorId = it.successorId,
        )
    },
    question = question,
    reason = reason,
)
