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
}
