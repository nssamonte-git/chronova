import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Screens render presentation-owned UI models: a file that declares composables, and every
 * `*UiState.kt` / `*UiModels.kt` file, in a feature presentation module or `:core:ui`, must not
 * import a domain entity, result or status type (the [denied] imports). ViewModels, `*UiMappers.kt`
 * files and helpers that run behind a ViewModel (no composables) may: they do the mapping.
 * Shared value vocabulary (enums, view settings) isn't denied.
 */
abstract class VerifyUiModelsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Regexes over the imported name after `noel.chronova.`. */
    @get:Input
    abstract val denied: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val patterns = denied.get().map(::Regex)
        val problems = mutableListOf<String>()
        var checked = 0
        for (file in sources.files.sortedBy { it.path }) {
            val text = file.readText()
            if ("@Composable" !in text && !file.name.endsWith("UiState.kt") && !file.name.endsWith("UiModels.kt")) continue
            checked++
            text.lineSequence()
                .filter { it.startsWith("import noel.chronova.") }
                .map { it.removePrefix("import noel.chronova.").substringBefore(" as ").trim() }
                .filter { name -> patterns.any { it.matches(name) } }
                .forEach { problems += "${file.path.substringAfter("/feature/").substringAfter("Android App/")}: imports $it; map it to a UI model in a ViewModel or *UiMappers.kt" }
        }
        val text = if (problems.isEmpty()) "OK: $checked screen and UI state files use UI models only\n" else problems.joinToString("\n", postfix = "\n")
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(text)
        if (problems.isNotEmpty()) throw GradleException("Screens use domain types:\n$text")
    }
}
