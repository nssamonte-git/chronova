// Schedules from images on Android: the import endpoint's client, and preparing photos and picked images.
plugins {
    id("chronova.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "noel.chronova.feature.chartimport.data"
}

dependencies {
    implementation(projects.feature.chartimport.domain)
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.okhttp.mockwebserver)

    // AndroidChartImagesTest: decodes, turns and scales real images on a device (its own test app).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
