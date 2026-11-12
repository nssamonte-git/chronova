import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Room with KSP; exported schemas are committed so migrations can be tested against them. */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        extensions.getByType(KspExtension::class.java).arg("room.schemaLocation", "$projectDir/schemas")
        addDependency("implementation", "androidx-room-runtime")
        addDependency("implementation", "androidx-room-ktx")
        addDependency("ksp", "androidx-room-compiler")
    }
}
