import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Screenshot tests for an Android library with Compose (apply after its Compose convention):
 * Compose Preview Screenshot Testing through AGP test suites. `@PreviewTest` previews in
 * `src/screenshotTest/kotlin` are rendered on the JVM and compared with the reference PNGs in
 * `src/screenshotTestDefaultDebug/reference`. Update them with
 * `updateScreenshotTestDefaultDebugTestSuite`, check with `testScreenshotTestDefaultDebugTestSuite`.
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val engine = libs.findVersion("screenshotEngine").get().requiredVersion
        val bom = dependencies.platform(library("androidx-compose-bom"))
        val tooling = library("androidx-compose-ui-tooling")
        val validationApi = library("screenshot-validation-api")
        val testOptions = extensions.getByType(LibraryExtension::class.java).testOptions
        // The preview renderer resolves the module's resources and theme attributes.
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.screenshotTests.create("screenshotTest") { suite ->
            suite.engineVersion = engine
            suite.targetVariants.add("debug")
            suite.dependencies {
                implementation.add(bom)
                implementation.add(tooling)
                implementation.add(validationApi)
            }
        }
        // AGP sets up preview rendering for every variant and needs each one's unit-test setup, but
        // release has unit tests off by default. Turn the setup on and skip running those tests,
        // so `test` doesn't run every unit test twice.
        extensions.getByType(LibraryAndroidComponentsExtension::class.java).beforeVariants { variant ->
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
        }
        tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { it.enabled = false }
        Unit
    }
}
