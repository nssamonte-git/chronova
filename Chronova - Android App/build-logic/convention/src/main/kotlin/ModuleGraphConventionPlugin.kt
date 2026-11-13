import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Root project only: registers `verifyModuleGraph`, which fails on a dependency between
 * modules that the three-layer architecture forbids (presentation -> domain <- data, features
 * never depend on each other, core never depends on features) and on a domain module built
 * with the Android plugin. Test dependencies follow the same rules, and may also use :core:testing.
 */
class ModuleGraphConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        check(target == target.rootProject) { "chronova.module.graph applies to the root project only" }
        val modules = target.provider { target.subprojects.map { it.moduleInfo() } }
        target.tasks.register("verifyModuleGraph", VerifyModuleGraphTask::class.java) { task ->
            task.group = "verification"
            task.description = "Checks module dependencies against the three-layer architecture."
            task.edges.set(modules.map { list -> list.flatMap { it.edges } })
            task.testEdges.set(modules.map { list -> list.flatMap { it.testEdges } })
            task.androidModules.set(modules.map { list -> list.filter { it.android }.map { it.path } })
            task.report.set(target.layout.buildDirectory.file("reports/module-graph.txt"))
        }
        target.tasks.register("verifyUiModels", VerifyUiModelsTask::class.java) { task ->
            task.group = "verification"
            task.description = "Checks that screens and UI state use UI models, not domain types."
            task.sources.from(
                target.fileTree(target.rootDir) {
                    it.include("feature/*/presentation/src/main/**/*.kt", "core/ui/src/main/**/*.kt")
                    it.exclude("**/build/**")
                },
            )
            task.denied.set(UI_DENIED_IMPORTS)
            task.report.set(target.layout.buildDirectory.file("reports/ui-models.txt"))
        }
    }

    private companion object {
        /** Domain entities, results and statuses that screens get as UI models instead. */
        val UI_DENIED_IMPORTS = listOf(
            """core\.model\.(SchedulerTask|TaskDependency|Project|ProjectSummary|TaskOutline)""",
            """core\.domain\.(SampleTasks|TaskProblem|ProjectProblem)""",
            """core\.domain\.schedule\.(ScheduleChange|ApplyResult|ScheduleState)""",
            """core\.domain\.notifications\.AppNotification""",
            """core\.domain\.sync\.(SyncStatus|SyncPrompt)""",
            """core\.domain\.auth\.(AuthState|AuthProblem|AuthOutcome)""",
            """feature\.[a-z]+\.domain\.(PreviewItem|ScheduleWarning|VoicePlan|Interpretation|ExplainedChange|SaveProjectResult|SyncOverview|ProjectSchedule|ExportResult|ExportedFile|DependentsPlan|PushResult)""",
            """feature\.[a-z]+\.domain\.schedule\.CommandProblem""",
        )
    }

    private class ModuleInfo(val path: String, val android: Boolean, val edges: List<String>, val testEdges: List<String>)

    private fun Project.moduleInfo(): ModuleInfo {
        fun targets(names: List<String>) = names.flatMap { name ->
            configurations.findByName(name)?.dependencies?.withType(ProjectDependency::class.java)?.map { it.path }.orEmpty()
        }.distinct()
        val main = targets(listOf("api", "implementation", "compileOnly", "runtimeOnly"))
        val test = targets(listOf("testImplementation", "testCompileOnly", "testRuntimeOnly", "androidTestImplementation", "androidTestCompileOnly"))
        val android = pluginManager.hasPlugin("com.android.library") || pluginManager.hasPlugin("com.android.application")
        return ModuleInfo(path, android, main.map { "$path->$it" }, test.map { "$path->$it" })
    }
}

abstract class VerifyModuleGraphTask : DefaultTask() {
    /** "from->to" for every main-code project dependency. */
    @get:Input
    abstract val edges: ListProperty<String>

    /** "from->to" for every test-only project dependency. */
    @get:Input
    abstract val testEdges: ListProperty<String>

    @get:Input
    abstract val androidModules: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val problems = mutableListOf<String>()
        for (edge in edges.get()) {
            val (from, to) = edge.split("->")
            problemWith(from, to)?.let { problems += "$from -> $to: $it" }
        }
        for (edge in testEdges.get()) {
            val (from, to) = edge.split("->")
            // Tests may use the shared fakes; otherwise they follow the module's own rules.
            if (to != ":core:testing") problemWith(from, to)?.let { problems += "$from -> $to (tests): $it" }
        }
        for (module in androidModules.get()) {
            if (module.isPureModule()) problems += "$module: a domain/pure module must use chronova.jvm.library, not an Android plugin"
        }
        val text = if (problems.isEmpty()) "OK: ${edges.get().size} module dependencies and ${testEdges.get().size} test dependencies checked\n" else problems.joinToString("\n", postfix = "\n")
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(text)
        if (problems.isNotEmpty()) throw GradleException("Module graph breaks the architecture:\n$text")
    }

    private fun String.isPureModule() = this in PURE_CORE || endsWith(":domain") && startsWith(":feature:")

    /** Why [from] may not depend on [to], or null when it may. */
    private fun problemWith(from: String, to: String): String? {
        if (from == ":app") return null
        if (to == ":app") return "nothing depends on the app shell"
        if (from.startsWith(":core:")) {
            return when {
                !to.startsWith(":core:") -> "core modules never depend on features"
                from in PURE_CORE && to !in PURE_CORE -> "pure core modules depend only on pure core modules"
                to == ":core:testing" -> "only tests use :core:testing"
                else -> null
            }
        }
        val feature = from.split(":").getOrNull(2) ?: return "unknown module"
        val layer = from.split(":").getOrNull(3) ?: return "a feature module is :feature:<name>:<layer>"
        if (to.startsWith(":feature:")) {
            val toFeature = to.split(":")[2]
            val toLayer = to.split(":").getOrNull(3)
            if (toFeature != feature) return "features never depend on another feature (wire them in the app shell)"
            return when {
                toLayer != "domain" -> "a layer depends only on its own feature's domain"
                layer == "domain" -> "a domain module doesn't depend on itself"
                else -> null
            }
        }
        val allowed = when (layer) {
            "domain" -> PURE_CORE
            "data" -> PURE_CORE + setOf(":core:database", ":core:network", ":core:data")
            "presentation" -> PURE_CORE + setOf(":core:designsystem", ":core:ui")
            else -> return "unknown layer '$layer'"
        }
        return if (to in allowed) null else "$layer may not use $to"
    }

    private companion object {
        val PURE_CORE = setOf(":core:model", ":core:timeline", ":core:domain")
    }
}
