// The voice assistant's rules and use cases: planning spoken commands against the schedule,
// clashes and alternatives, the conversation's server and hardware ports. Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.timeline)
    testImplementation(projects.core.testing)
}
