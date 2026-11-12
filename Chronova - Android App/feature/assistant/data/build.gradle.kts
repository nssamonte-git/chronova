// The voice endpoints' client (docs/voice-api.md), the microphone recorder, the answer player
// and the assistant's remembered settings.
plugins {
    id("chronova.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "noel.chronova.feature.assistant.data"
}

dependencies {
    implementation(projects.feature.assistant.domain)
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.okhttp.mockwebserver)
}
