package noel.chronova.feature.assistant.data.remote

import noel.chronova.feature.assistant.domain.ListContext
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import noel.chronova.core.network.ApiJson
import noel.chronova.core.network.await
import noel.chronova.feature.assistant.domain.ExplainRequest
import noel.chronova.feature.assistant.domain.Proposal
import noel.chronova.feature.assistant.domain.ProposedOperation
import noel.chronova.feature.assistant.domain.ScheduleWarning
import noel.chronova.feature.assistant.domain.SpokenAnswer
import noel.chronova.feature.assistant.domain.Transcript
import noel.chronova.feature.assistant.domain.VoiceApi
import noel.chronova.feature.assistant.domain.VoiceApiException
import noel.chronova.feature.assistant.domain.VoiceContext
import noel.chronova.feature.assistant.domain.VoiceDecision
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpVoiceApi(
    baseUrl: String,
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : VoiceApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun transcribe(
        accessToken: String,
        audio: File,
        durationMs: Long,
        language: String?,
        keyterms: List<String>,
    ): Transcript {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", "command.m4a", audio.asRequestBody(M4A))
            .addFormDataPart("durationMs", durationMs.toString())
            .apply { if (language != null) addFormDataPart("language", language) }
            .apply { keyterms.forEach { addFormDataPart("keyterms[]", it) } }
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
                stage = request.stage.toWire(),
                warnings = request.warnings.map { it.toDto() }.ifEmpty { null },
                offer = request.offer.map { it.toDto() }.ifEmpty { null },
            )
            is ExplainRequest.Question -> ExplainRequestDto(question = request.text, language = request.language)
        }
        val body = ApiJson.json.encodeToString(ExplainRequestDto.serializer(), dto)
        val reply = call("explain", body.toRequestBody(JSON), ExplainResponseDto.serializer(), accessToken)
        val audio = try {
            reply.audio?.let { Base64.getDecoder().decode(it) }
        } catch (_: IllegalArgumentException) {
            null // Unreadable audio: the words are still worth showing.
        }
        return SpokenAnswer(reply.text, audio, if (audio != null) reply.audioType else null)
    }

    override suspend fun interpret(accessToken: String, transcript: String, context: VoiceContext): Proposal {
        val body = ApiJson.json.encodeToString(InterpretRequestDto.serializer(), InterpretRequestDto(transcript, context.toDto(), APP_CAPABILITIES))
        return call("interpret", body.toRequestBody(JSON), InterpretResponseDto.serializer(), accessToken).toDomain()
    }

    override suspend fun interpretOnList(accessToken: String, transcript: String, context: ListContext): Proposal {
        val body = ApiJson.json.encodeToString(ListInterpretRequestDto.serializer(), ListInterpretRequestDto(transcript, context.toDto(), APP_CAPABILITIES))
        return call("interpret", body.toRequestBody(JSON), InterpretResponseDto.serializer(), accessToken).toDomain()
    }

    override suspend fun resolve(
        accessToken: String,
        transcript: String,
        context: VoiceContext,
        planned: List<ProposedOperation>,
        clashes: List<ScheduleWarning>,
    ): Proposal {
        val body = buildJsonObject {
            put("transcript", transcript)
            put("context", ApiJson.json.encodeToJsonElement(VoiceContextDto.serializer(), context.toDto()))
            put("planned", JsonArray(planned.map { it.toWire() }))
            put("clashes", ApiJson.json.encodeToJsonElement(ListSerializer(ExplainWarningDto.serializer()), clashes.map { it.toDto() }))
        }
        return call("resolve", body.toString().toRequestBody(JSON), InterpretResponseDto.serializer(), accessToken).toDomain()
    }

    override suspend fun decide(accessToken: String, decision: VoiceDecision) {
        val dto = DecisionRequestDto(
            said = decision.said,
            proposed = decision.proposed.map { it.toDto() },
            offered = decision.offered.map { it.toDto() }.ifEmpty { null },
            answer = decision.answer.toWire(),
            reply = decision.reply,
        )
        send("decision", ApiJson.json.encodeToString(DecisionRequestDto.serializer(), dto).toRequestBody(JSON), accessToken)
    }

    /** The server's health check (`GET /up`), answered within [REACHABLE_TIMEOUT_SECONDS] or counted as unreachable. */
    override suspend fun reachable(): Boolean = withContext(io) {
        val request = Request.Builder().url("$base/up").get().build()
        try {
            healthClient.newCall(request).await().use { it.isSuccessful }
        } catch (_: IOException) {
            false
        }
    }

    private val healthClient: OkHttpClient by lazy {
        client.newBuilder().callTimeout(REACHABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    }

    // Callers are often on the main thread (ViewModels): the whole call, body reading included, runs on [io].
    private suspend fun <T> call(
        endpoint: String,
        body: RequestBody,
        reply: KSerializer<T>,
        accessToken: String,
    ): T = withContext(io) {
        val text = send(endpoint, body, accessToken)
        try {
            ApiJson.json.decodeFromString(reply, text)
        } catch (error: SerializationException) {
            throw VoiceApiException(VoiceApiException.Kind.UNAVAILABLE, error)
        } catch (error: IllegalArgumentException) {
            throw VoiceApiException(VoiceApiException.Kind.UNAVAILABLE, error)
        }
    }

    /** Posts [body] and returns the reply's text (empty for 204). */
    private suspend fun send(endpoint: String, body: RequestBody, accessToken: String): String = withContext(io) {
        val request = Request.Builder()
            .url("$base/v1/voice/$endpoint")
            .post(body)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()
        try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    val tooLong = response.code == 422 && TOO_LONG in response.body.string()
                    throw VoiceApiException(if (tooLong) VoiceApiException.Kind.TOO_LONG else voiceErrorFor(response.code))
                }
                response.body.string()
            }
        } catch (error: IOException) {
            throw VoiceApiException(VoiceApiException.Kind.NETWORK, error)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val M4A = "audio/mp4".toMediaType()
        const val TOO_LONG = "transcript_too_long"
        const val REACHABLE_TIMEOUT_SECONDS = 5L
    }
}
