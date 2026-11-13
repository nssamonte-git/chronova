import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

/** An Android library module (data and presentation layers, Android-bound core modules). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.getByType(LibraryExtension::class.java).apply {
            compileSdk { version = release(Chronova.COMPILE_SDK) }
            defaultConfig {
                minSdk = Chronova.MIN_SDK
                testInstrumentationRunner = Chronova.TEST_RUNNER
            }
            testOptions.managedDevices.addTestEmulator()
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
        addUnitTestDependencies()
    }
}
