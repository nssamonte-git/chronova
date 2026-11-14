package noel.chronova.app.auth

import noel.chronova.app.BuildConfig

/**
 * Where the Chronova server lives: the live server by default, or `chronova.apiUrl`
 * from the uncommitted local.properties (app/build.gradle.kts). Without a usable URL
 * the app can't sign in and stops at the sign-in page.
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
