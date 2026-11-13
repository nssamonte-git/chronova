import java.util.Properties

plugins {
    id("chronova.android.application")
    id("chronova.android.compose")
    alias(libs.plugins.kotlin.serialization)
    // Reads google-services.json (Firebase project chronova-ece3c) for push notifications.
    alias(libs.plugins.google.services)
}

/**
 * Sync server URL from the (uncommitted) local.properties: `chronova.apiUrl`.
 * Without it the app builds offline-only.
 */
val backendProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun backendSetting(key: String): String {
    val value = backendProperties.getProperty("chronova.$key", "").trim()
    require(value.none { it == '"' || it == '\\' }) { "chronova.$key must not contain quotes or backslashes" }
    return "\"$value\""
}

android {
    namespace = "noel.chronova.app"

    defaultConfig {
        applicationId = "noel.chronova.app"
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SYNC_API_URL", backendSetting("apiUrl"))
    }

    buildTypes {
        release {
            // R8 shrinks and obfuscates the whole app, its own code included (the libraries ship their keep rules).
            optimization {
                enable = true
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.timeline)
    implementation(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.feature.notifications.domain)
    implementation(projects.feature.notifications.data)
    implementation(projects.feature.notifications.presentation)
    implementation(projects.feature.projects.domain)
    implementation(projects.feature.projects.presentation)
    implementation(projects.feature.timeline.domain)
    implementation(projects.feature.timeline.presentation)
    implementation(projects.feature.sync.domain)
    implementation(projects.feature.sync.data)
    implementation(projects.feature.sync.presentation)
    implementation(projects.feature.account.domain)
    implementation(projects.feature.account.data)
    implementation(projects.feature.account.presentation)
    implementation(projects.feature.assistant.domain)
    implementation(projects.feature.assistant.data)
    implementation(projects.feature.assistant.presentation)
    implementation(projects.feature.export.domain)
    implementation(projects.feature.export.data)
    implementation(projects.feature.export.presentation)
    implementation(projects.feature.reminders.domain)
    implementation(projects.feature.reminders.data)
    implementation(projects.feature.push.domain)
    implementation(projects.feature.push.data)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    // Hosts composables in instrumented tests (createComposeRule); debug builds only.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Rotation tests: Compose's StateRestorationTester and UiAutomator's device rotation.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

// Mobile build validator: stop the build on unfixed security findings
apply(from = "$rootDir/build-validator/build-validator.gradle")
