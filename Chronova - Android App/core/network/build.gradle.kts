// What every server endpoint shares: the HTTP client and the JSON settings. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
}
