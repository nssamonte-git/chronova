package noel.chronova.app.auth

import noel.chronova.app.BuildConfig

/**
 * Where the Chronova server lives, from `chronova.apiUrl` in the uncommitted
 * local.properties. A build without it works fully offline and offers no sign-in.
 * Release builds need https; debug builds may also use http://localhost (the phone
 * reaches a server on the development machine through `adb reverse`).
 */
data class BackendConfig(val apiUrl: String, val allowLocalHttp: Boolean = false) {
    val isComplete: Boolean
        get() = apiUrl.startsWith("https://") ||
            (allowLocalHttp && (apiUrl.startsWith("http://localhost") || apiUrl.startsWith("http://127.0.0.1")))

    companion object {
        fun fromBuildConfig() = BackendConfig(apiUrl = BuildConfig.SYNC_API_URL, allowLocalHttp = BuildConfig.DEBUG)
    }
}
