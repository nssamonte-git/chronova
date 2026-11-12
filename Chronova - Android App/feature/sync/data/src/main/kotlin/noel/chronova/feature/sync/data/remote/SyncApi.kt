package noel.chronova.feature.sync.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import noel.chronova.core.network.ApiJson
import noel.chronova.core.network.await

/** The sync backend. Every call needs a Cognito access token. */
interface SyncApi {
    suspend fun sync(accessToken: String, request: SyncRequestDto): SyncResponseDto

    /** Deletes the caller's synced data and Cognito user. */
    suspend fun deleteAccount(accessToken: String)
}

class SyncApiException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind {
        /** Token missing, expired or revoked: sign in again. */
        UNAUTHORIZED,

        /** Another device of the same user is syncing; retry shortly. */
        BUSY,

        /** The server refused the request as invalid (a bug, not retried). */
        REJECTED,
        SERVER,
        NETWORK,
    }

    companion object {
        fun forStatus(code: Int): Kind = when (code) {
            401, 403 -> Kind.UNAUTHORIZED
            409 -> Kind.BUSY
            in 400..499 -> Kind.REJECTED
            else -> Kind.SERVER
        }
    }
}

class OkHttpSyncApi(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) : SyncApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun sync(accessToken: String, request: SyncRequestDto): SyncResponseDto {
        val body = ApiJson.json.encodeToString(SyncRequestDto.serializer(), request).toRequestBody(JSON)
        val response = call(Request.Builder().url("$base/v1/sync").post(body).bearer(accessToken).build())
        return try {
            ApiJson.json.decodeFromString(SyncResponseDto.serializer(), response)
        } catch (error: IllegalArgumentException) {
            throw SyncApiException(SyncApiException.Kind.SERVER, error)
        }
    }

    override suspend fun deleteAccount(accessToken: String) {
        call(Request.Builder().url("$base/v1/account").delete().bearer(accessToken).build())
    }

    private suspend fun call(request: Request): String {
        val response = try {
            client.newCall(request).await()
        } catch (error: IOException) {
            throw SyncApiException(SyncApiException.Kind.NETWORK, error)
        }
        return response.use {
            if (!it.isSuccessful) throw SyncApiException(SyncApiException.forStatus(it.code))
            try {
                it.body.string()
            } catch (error: IOException) {
                throw SyncApiException(SyncApiException.Kind.NETWORK, error)
            }
        }
    }

    private fun Request.Builder.bearer(token: String) =
        header("Authorization", "Bearer $token").header("Accept", "application/json")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
