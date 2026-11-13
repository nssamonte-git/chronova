// Task reminders: which reminders are due, the next alarm, and the engine that posts them. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.domain)

    testImplementation(projects.core.testing)
}
