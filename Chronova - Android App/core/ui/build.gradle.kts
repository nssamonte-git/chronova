// UI pieces more than one feature shows: time/date formatting and the sync status line and button.
plugins {
    id("chronova.android.library")
    id("chronova.android.compose")
}

android {
    namespace = "noel.chronova.core.ui"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.compose.material.icons.core)
}
