package noel.chronova.app.auth

import noel.chronova.app.BuildConfig

/**
 * Where the sync backend lives, from the CDK stack outputs placed in local.properties
 * (`chronova.region`, `chronova.userPoolId`, `chronova.userPoolClientId`, `chronova.apiUrl`).
 * A build without them works fully offline and offers no sign-in.
 */
data class BackendConfig(
    val region: String,
    val userPoolId: String,
    val userPoolClientId: String,
    val apiUrl: String,
) {
    val isComplete: Boolean
        get() = listOf(region, userPoolId, userPoolClientId, apiUrl).all { it.isNotBlank() } && apiUrl.startsWith("https://")

    companion object {
        fun fromBuildConfig() = BackendConfig(
            region = BuildConfig.SYNC_REGION,
            userPoolId = BuildConfig.SYNC_USER_POOL_ID,
            userPoolClientId = BuildConfig.SYNC_USER_POOL_CLIENT_ID,
            apiUrl = BuildConfig.SYNC_API_URL,
        )
    }
}
