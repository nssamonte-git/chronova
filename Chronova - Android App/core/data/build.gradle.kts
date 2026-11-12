// Repositories over the shared database (projects, tasks, links, view settings) and connectivity.
plugins {
    id("chronova.android.library")
}

android {
    namespace = "noel.chronova.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.database)

    // RoomProjectRepositoryTest: a real Room database on a device (its own test APK, not the app's data).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
