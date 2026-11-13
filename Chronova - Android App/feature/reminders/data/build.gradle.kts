// Task reminders on Android: one AlarmManager alarm, the notifications, and the receivers that wake the engine.
plugins {
    id("chronova.android.library")
}

android {
    namespace = "noel.chronova.feature.reminders.data"
}

dependencies {
    implementation(projects.feature.reminders.domain)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
}
