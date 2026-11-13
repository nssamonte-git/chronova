// Push notifications (docs/push.md): what a push says, and registering this device. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.domain)

    testImplementation(projects.core.testing)
}
