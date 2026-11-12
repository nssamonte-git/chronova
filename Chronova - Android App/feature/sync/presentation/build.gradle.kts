// The app-wide sync ViewModel and the reconnect prompt. The status line and button are in :core:ui.
plugins {
    id("chronova.android.library")
    id("chronova.android.compose")
}

android {
    namespace = "noel.chronova.feature.sync.presentation"
}

dependencies {
    implementation(projects.feature.sync.domain)
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(projects.core.testing)

    // On-device Compose tests: this module's own test app (see HANDOFF), never the app's data.
    androidTestImplementation(projects.core.testing)
    androidTestImplementation(projects.core.designsystem)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}
