// The sign-in pages (sign in, sign up, confirm, reset) and the account page.
plugins {
    id("chronova.android.library")
    id("chronova.android.compose")
    id("chronova.android.screenshots")
}

android {
    namespace = "noel.chronova.feature.account.presentation"
}

dependencies {
    implementation(projects.feature.account.domain)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // On-device Compose tests: this module's own test app (see HANDOFF), never the app's data.
    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}
