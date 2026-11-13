// Project export on Android: the PDF Gantt chart (PdfDocument) and the files shared through a FileProvider.
plugins {
    id("chronova.android.library")
}

android {
    namespace = "noel.chronova.feature.export.data"
}

dependencies {
    implementation(projects.feature.export.domain)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)

    // GanttPdfWriterTest: renders real PDFs on a device (its own test app).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
