// Push notifications on Android: Firebase Cloud Messaging (the token and incoming messages) and the
// server's devices endpoint. The app module applies the google-services plugin.
plugins {
    id("chronova.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "noel.chronova.feature.push.data"
}

dependencies {
    implementation(projects.feature.push.domain)
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.okhttp.mockwebserver)
}
