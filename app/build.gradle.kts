import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Sync backend settings from the (uncommitted) local.properties, as printed by
 * `npx cdk deploy` in backend/. Missing values build an offline-only app.
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
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "noel.chronova.app"
        // 26+: java.time is used for timeline labels without core-library desugaring.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SYNC_REGION", backendSetting("region"))
        buildConfigField("String", "SYNC_USER_POOL_ID", backendSetting("userPoolId"))
        buildConfigField("String", "SYNC_USER_POOL_CLIENT_ID", backendSetting("userPoolClientId"))
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
    compileOptions {
        // Required by Amplify (Cognito auth).
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    // Exported schemas are committed so future migrations can be tested against them.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.amplify.core.kotlin)
    implementation(libs.amplify.auth.cognito)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
