// Project list use cases: list, save (validated) and delete projects. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.domain)
}

dependencies {
    testImplementation(projects.core.testing)
}
