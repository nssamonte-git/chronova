package noel.chronova.app.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import noel.chronova.app.core.network.ApiJson

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

/** Runs the call without blocking a thread; cancelling the coroutine cancels the request. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        },
    )
}
