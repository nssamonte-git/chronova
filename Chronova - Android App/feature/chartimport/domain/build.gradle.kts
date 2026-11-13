// Schedules from images (docs/import-api.md): what the server read, the preview, and planning and applying it. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.domain)

    testImplementation(projects.core.testing)
}
