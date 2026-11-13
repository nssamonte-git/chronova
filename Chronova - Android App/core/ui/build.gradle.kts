// UI pieces more than one feature shows: time/date formatting and the sync status line and button.
plugins {
    id("chronova.android.library")
    id("chronova.android.compose")
    id("chronova.android.screenshots")
}

android {
    namespace = "noel.chronova.core.ui"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.compose.material.icons.core)

    // On-device Compose tests: this module's own test app (see HANDOFF), not the app's data.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}
