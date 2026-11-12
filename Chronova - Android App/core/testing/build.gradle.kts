// Fakes for unit tests (testImplementation only). Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.domain)
}
