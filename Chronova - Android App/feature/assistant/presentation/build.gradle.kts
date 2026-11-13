// The voice assistant's UI: the 3D orb (Filament, assets/orb), the overlay, its words, the
// ViewModel, and the AssistantSlot the app hands to the timeline.
plugins {
    id("chronova.android.library")
    id("chronova.android.compose")
    id("chronova.android.screenshots")
}

android {
    namespace = "noel.chronova.feature.assistant.presentation"
}

dependencies {
    implementation(projects.feature.assistant.domain)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    // The assistant orb: a live 3D model (assets/orb/assistant_orb.glb).
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)
    implementation(libs.filament.utils)

    testImplementation(projects.core.testing)
}
