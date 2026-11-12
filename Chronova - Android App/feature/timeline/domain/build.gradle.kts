// Timeline use cases: the project's schedule, edits through the one apply path, the task
// editor's save, delete/undo and view settings. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.domain)
    testImplementation(projects.core.testing)
}
