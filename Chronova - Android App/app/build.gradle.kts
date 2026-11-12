import java.util.Properties

plugins {
    id("chronova.android.application")
    id("chronova.android.compose")
    alias(libs.plugins.kotlin.serialization)
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
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
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
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    // The assistant orb: a live 3D model (assets/orb/assistant_orb.glb).
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)
    implementation(libs.filament.utils)
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
