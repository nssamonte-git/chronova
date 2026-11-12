// Sign-in, sign-up, password reset and account page use cases, on the auth and sync contracts. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.domain)

    testImplementation(projects.core.testing)
}
