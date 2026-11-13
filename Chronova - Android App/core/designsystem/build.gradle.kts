// The app's look: theme, colours (task colours included) and shared icons/brand images.
plugins {
    id("chronova.android.library")
    id("chronova.android.compose")
    id("chronova.android.screenshots")
}

android {
    namespace = "noel.chronova.core.designsystem"
}

dependencies {
    implementation(projects.core.model)

    // On-device Compose tests: this module's own test app (see HANDOFF), not the app's data.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}
