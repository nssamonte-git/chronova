import com.android.build.api.dsl.ManagedDevices
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

/** The shared version catalog (`gradle/libs.versions.toml`). */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun Project.library(alias: String) = libs.findLibrary(alias).orElseThrow {
    IllegalStateException("No library '$alias' in gradle/libs.versions.toml")
}

internal fun Project.addDependency(configuration: String, alias: String) {
    dependencies.add(configuration, library(alias))
}

/** Settings every module shares. */
internal object Chronova {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37

    /** 26+: java.time without core-library desugaring. */
    const val MIN_SDK = 26
    const val TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"

    /** The managed emulator device tests run on (`tools/check.sh --devices`). */
    const val TEST_DEVICE = "pixel6api35"
}

/** Every module's device tests can run on a throwaway emulator: `./gradlew pixel6api35DebugAndroidTest`. */
internal fun ManagedDevices.addTestEmulator() {
    localDevices.create(Chronova.TEST_DEVICE) { device ->
        device.device = "Pixel 6"
        device.sdkVersion = 35
        device.systemImageSource = "google"
        device.require64Bit = true
    }
}

/** Unit-test libraries every module gets. */
internal fun Project.addUnitTestDependencies() {
    addDependency("testImplementation", "junit")
    addDependency("testImplementation", "kotlinx-coroutines-test")
}
