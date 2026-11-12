package noel.chronova.core.network

import kotlinx.serialization.json.Json

/** JSON settings for every server endpoint (sync, auth, voice). */
object ApiJson {
    /** Unknown fields from a newer server are ignored; nulls are left out. */
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
