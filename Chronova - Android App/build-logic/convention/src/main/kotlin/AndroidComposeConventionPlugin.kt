import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Compose for an Android application or library module (apply after its Android convention). */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
        extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true
        val bom = dependencies.platform(library("androidx-compose-bom"))
        dependencies.add("implementation", bom)
        addDependency("implementation", "androidx-compose-ui")
        addDependency("implementation", "androidx-compose-ui-graphics")
        addDependency("implementation", "androidx-compose-ui-tooling-preview")
        addDependency("implementation", "androidx-compose-material3")
        addDependency("debugImplementation", "androidx-compose-ui-tooling")
    }
}
