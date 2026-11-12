// Server sign-in (Sanctum tokens) with the session kept encrypted by the Android keystore.
plugins {
    id("chronova.android.library")
}

android {
    namespace = "noel.chronova.feature.account.data"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.okhttp.mockwebserver)
}
