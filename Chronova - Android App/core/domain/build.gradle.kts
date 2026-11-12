// Contracts and rules every feature shares: repositories, auth and sync contracts,
// schedule changes and their one apply path. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.timeline)
    testImplementation(projects.core.testing)
}
