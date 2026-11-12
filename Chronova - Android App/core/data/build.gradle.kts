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
}
