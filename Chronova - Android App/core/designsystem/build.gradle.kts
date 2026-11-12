// The app's look: theme, colours (task colours included) and shared icons/brand images.
plugins {
    id("chronova.android.library")
    id("chronova.android.compose")
}

android {
    namespace = "noel.chronova.core.designsystem"
}

dependencies {
    implementation(projects.core.model)
}
