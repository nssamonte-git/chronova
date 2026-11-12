// The project list and the create/edit sheet. Projects are stored by :core:data.
plugins {
    id("chronova.android.library")
    id("chronova.android.compose")
}

android {
    namespace = "noel.chronova.feature.projects.presentation"
}

dependencies {
    implementation(projects.feature.projects.domain)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(projects.core.testing)

    // On-device Compose tests: this module's own test app (see HANDOFF), never the app's data.
    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}
