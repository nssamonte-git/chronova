// What every server endpoint shares: the HTTP client and the JSON settings. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
    // For the @Serializable class in the tests.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
}
