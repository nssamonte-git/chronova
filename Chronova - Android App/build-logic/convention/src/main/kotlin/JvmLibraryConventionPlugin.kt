import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * A pure Kotlin module (domain layers, models, pure core). No Android plugin, so an
 * Android import is a compile error rather than a review comment.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.getByType(JavaPluginExtension::class.java).apply {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        extensions.getByType(KotlinJvmProjectExtension::class.java).compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        addDependency("implementation", "kotlinx-coroutines-core")
        addUnitTestDependencies()
    }
}
