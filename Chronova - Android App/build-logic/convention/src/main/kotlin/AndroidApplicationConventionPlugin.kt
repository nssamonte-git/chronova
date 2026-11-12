import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

/** The `:app` shell: SDK levels, Java target and test runner. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.getByType(ApplicationExtension::class.java).apply {
            compileSdk { version = release(Chronova.COMPILE_SDK) }
            defaultConfig {
                minSdk = Chronova.MIN_SDK
                targetSdk = Chronova.TARGET_SDK
                testInstrumentationRunner = Chronova.TEST_RUNNER
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
            // :app:lintDebug also checks every module it depends on, as it did when all code lived in :app.
            lint { checkDependencies = true }
        }
        addUnitTestDependencies()
    }
}
