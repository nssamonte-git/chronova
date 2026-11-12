// Sync with the server (docs/sync-protocol.md): the API client and DTOs, the engine, the
// app-wide coordinator and the Room-backed local store.
plugins {
    id("chronova.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "noel.chronova.feature.sync.data"
}

dependencies {
    implementation(projects.feature.sync.domain)
    implementation(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(libs.androidx.core.ktx)
}
