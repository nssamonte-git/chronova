package noel.chronova.feature.chartimport.data

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import noel.chronova.core.model.Project
import noel.chronova.core.network.ApiJson
import noel.chronova.core.network.await
import noel.chronova.feature.chartimport.domain.ImportApi
import noel.chronova.feature.chartimport.domain.ImportApiException
import noel.chronova.feature.chartimport.domain.ImportedSchedule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * `POST /v1/import/image` (docs/import-api.md): the prepared JPEG and what helps read it. Reading
 * takes Meta and OpenAI a while, so [client] should allow about a minute and a half.
 */
class OkHttpImportApi(
    baseUrl: String,
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ImportApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun read(accessToken: String, image: ByteArray, timeZone: String, today: LocalDate, project: Project?): ImportedSchedule =
        withContext(io) {
            val form = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "chart.jpg", image.toRequestBody(JPEG))
                .addFormDataPart("mode", if (project == null) "new_project" else "add_to_project")
                .addFormDataPart("timeZone", timeZone)
                .addFormDataPart("today", today.toString())
                .apply {
                    if (project != null) {
                        addFormDataPart("projectName", project.name)
                        addFormDataPart("projectStart", project.startDate.toString())
                        addFormDataPart("projectEnd", project.endDate.toString())
                    }
                }
                .build()
            val request = Request.Builder()
                .url("$base/v1/import/image")
                .post(form)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .build()
            val text = try {
                client.newCall(request).await().use { response ->
                    if (!response.isSuccessful) throw ImportApiException(errorFor(response.code, response.body.string()))
                    response.body.string()
                }
            } catch (error: IOException) {
                throw ImportApiException(ImportApiException.Kind.NETWORK, error)
            }
            try {
                ApiJson.json.decodeFromString(ImportResponseDto.serializer(), text).toDomain()
            } catch (error: SerializationException) {
                throw ImportApiException(ImportApiException.Kind.UNAVAILABLE, error)
            } catch (error: IllegalArgumentException) {
                throw ImportApiException(ImportApiException.Kind.UNAVAILABLE, error)
            }
        }

    private companion object {
        val JPEG = "image/jpeg".toMediaType()

        fun errorFor(code: Int, body: String): ImportApiException.Kind = when {
            code == 401 || code == 403 -> ImportApiException.Kind.UNAUTHORIZED
            code == 429 -> ImportApiException.Kind.RATE_LIMITED
            code == 503 -> ImportApiException.Kind.DISABLED
            code == 422 && ("invalid_image" in body || "\"image\"" in body) -> ImportApiException.Kind.BAD_IMAGE
            else -> ImportApiException.Kind.UNAVAILABLE
        }
    }
}
