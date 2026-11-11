package noel.chronova.app.data.remote

import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
    /** Sends a recording (AAC in MPEG-4) and returns what was said; `""` when nothing was. */
    suspend fun transcribe(accessToken: String, audio: File, durationMs: Long, language: String?): String

    /** Asks the server to interpret [transcript]; cancelling the coroutine cancels the request. */
    suspend fun interpret(accessToken: String, transcript: String, context: VoiceContext): Proposal
}

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

    override suspend fun transcribe(accessToken: String, audio: File, durationMs: Long, language: String?): String {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", "command.m4a", audio.asRequestBody(M4A))
            .addFormDataPart("durationMs", durationMs.toString())
            .apply { if (language != null) addFormDataPart("language", language) }
            .build()
        return call("transcribe", form, TranscribeResponseDto.serializer(), accessToken).text.trim()
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
