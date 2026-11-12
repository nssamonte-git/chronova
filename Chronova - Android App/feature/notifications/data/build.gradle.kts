// The notifications kept on the phone (Room), with the undo of assistant changes stored as JSON.
plugins {
    id("chronova.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "noel.chronova.feature.notifications.data"
}

dependencies {
    implementation(projects.feature.notifications.domain)
    implementation(projects.core.domain)
    implementation(projects.core.database)
    implementation(libs.kotlinx.serialization.json)
}
