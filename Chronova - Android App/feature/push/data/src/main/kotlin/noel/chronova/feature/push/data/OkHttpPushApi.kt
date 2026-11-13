package noel.chronova.feature.push.data

import java.io.IOException
import kotlinx.serialization.Serializable
import noel.chronova.core.network.ApiJson
import noel.chronova.core.network.await
import noel.chronova.feature.push.domain.DeviceRegistration
import noel.chronova.feature.push.domain.PushApi
import noel.chronova.feature.push.domain.apiName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** `PUT /v1/devices` (docs/push.md). Failures throw [IOException]; the caller tries again later. */
class OkHttpPushApi(baseUrl: String, private val client: OkHttpClient) : PushApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun register(accessToken: String, device: DeviceRegistration) {
        val body = ApiJson.json.encodeToString(
            DeviceDto.serializer(),
            DeviceDto(device.token, device.platform, device.timeZone, device.reminderLead.apiName()),
        )
        val request = Request.Builder()
            .url("$base/v1/devices")
            .put(body.toRequestBody(JSON))
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).await().use { if (!it.isSuccessful) throw IOException("devices answered ${it.code}") }
    }

    @Serializable
    private data class DeviceDto(val token: String, val platform: String, val timeZone: String, val reminderLead: String)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
