// Project export: the CSV and calendar files, and the use case behind the export sheet. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.domain)

    testImplementation(projects.core.testing)
}
