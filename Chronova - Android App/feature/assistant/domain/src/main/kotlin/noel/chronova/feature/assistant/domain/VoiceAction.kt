package noel.chronova.feature.assistant.domain

import noel.chronova.core.model.ReminderLead
import noel.chronova.core.model.ThemeMode
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import java.time.LocalDate
import java.time.format.DateTimeParseException
import noel.chronova.core.model.TimeScale

/** One proposed action as the server sent it; which fields are set depends on [type]. */
data class ProposedAction(
    val type: String,
    val date: String? = null,
    val taskId: String? = null,
    /** `set_scale`: hours, days, weeks or months. */
    val scale: String? = null,
    /** `zoom`: in, out or reset. */
    val direction: String? = null,
    /** `row_height`: bigger or smaller. */
    val size: String? = null,
    /** `critical_path`: show it or hide it. */
    val on: Boolean? = null,
    /** `search`: words in task titles (or null), and the colour and kind chips. */
    val query: String? = null,
    val colors: List<String>? = null,
    val kinds: List<String>? = null,
    /** `open_project`, `export`. */
    val projectId: String? = null,
    /** `set_theme`: system, light or dark. */
    val theme: String? = null,
    /** `set_reminders`: off, at_start, 15_min, 1_hour or 1_day. */
    val lead: String? = null,
    /** `export`: pdf, csv or calendar, and whether notes go in. */
    val format: String? = null,
    val notes: Boolean? = null,
)

/**
 * Something the app does at once because it was asked to, without changing anything
 * (`docs/voice-api.md`, "actions"): unlike schedule edits it needs no spoken yes.
 */
sealed interface VoiceAction {
    data object GoToToday : VoiceAction
    data class GoToDate(val date: LocalDate) : VoiceAction

    /** Selects the task and scrolls to it; [title] is for what the assistant says. */
    data class ShowTask(val taskId: String, val title: String) : VoiceAction

    // How the timeline is shown (remembered for the project, like the settings row); never task times.
    data class SetScale(val scale: TimeScale) : VoiceAction
    data class Zoom(val step: ZoomStep) : VoiceAction
    data class RowHeight(val bigger: Boolean) : VoiceAction
    data class CriticalPath(val on: Boolean) : VoiceAction

    enum class ZoomStep { IN, OUT, RESET }

    /** Lists only the matching tasks, as the search bar does: [colors] any of them, each of [kinds]. */
    data class Search(val query: String?, val colors: Set<TaskColor>, val kinds: Set<SearchKind>) : VoiceAction
    data object ClearSearch : VoiceAction

    /** Opens the task's editor. */
    data class OpenTask(val taskId: String, val title: String) : VoiceAction

    // The app's: settings, sync and export (done by the shell, once said).
    data class SetTheme(val theme: ThemeMode) : VoiceAction
    data class SetReminders(val lead: ReminderLead) : VoiceAction
    data object SyncNow : VoiceAction
    data class Export(val projectId: String, val projectName: String, val format: ExportKind, val notes: Boolean) : VoiceAction

    enum class ExportKind { PDF, CSV, CALENDAR }

    // Leaving the screen (nothing follows these).
    data class OpenProject(val projectId: String, val name: String) : VoiceAction
    data object GoToProjects : VoiceAction
    data object OpenNotifications : VoiceAction
    data object OpenAccount : VoiceAction

    /** Leaves the screen. */
    val leavesScreen: Boolean get() = this is OpenProject || this == GoToProjects || this == OpenNotifications || this == OpenAccount

    /** Done by the screen it was asked on (scrolling, the view, the search); the rest is the app's. */
    val forTheScreen: Boolean get() = !leavesScreen && this !is SetTheme && this !is SetReminders && this != SyncNow && this !is Export

    enum class SearchKind { NOT_DONE, DONE, MILESTONE }

    companion object {
        /**
         * The action for [proposed], given the open project's [tasks] (none on the list of projects) and the
         * user's [projects]; null if it is malformed or names a task or project that isn't there.
         */
        fun from(proposed: ProposedAction, tasks: List<SchedulerTask>, projects: List<ProjectRef>): VoiceAction? = try {
            when (proposed.type) {
                "go_to_today" -> GoToToday
                "go_to_date" -> GoToDate(LocalDate.parse(proposed.date ?: return null))
                "show_task" -> tasks.firstOrNull { it.id == proposed.taskId }?.let { ShowTask(it.id, it.title) }
                "set_scale" -> TimeScale.entries.firstOrNull { it.name.equals(proposed.scale, ignoreCase = true) }?.let(::SetScale)
                "zoom" -> ZoomStep.entries.firstOrNull { it.name.equals(proposed.direction, ignoreCase = true) }?.let(::Zoom)
                "row_height" -> when (proposed.size) {
                    "bigger" -> RowHeight(bigger = true)
                    "smaller" -> RowHeight(bigger = false)
                    else -> null
                }
                "critical_path" -> proposed.on?.let(::CriticalPath)
                "search" -> search(proposed)
                "clear_search" -> ClearSearch
                "open_task" -> tasks.firstOrNull { it.id == proposed.taskId }?.let { OpenTask(it.id, it.title) }
                "set_theme" -> ThemeMode.entries.firstOrNull { it.name.equals(proposed.theme, ignoreCase = true) }?.let(::SetTheme)
                "set_reminders" -> LEADS[proposed.lead]?.let(::SetReminders)
                "sync_now" -> SyncNow
                "export" -> {
                    val project = projects.firstOrNull { it.id == proposed.projectId }
                    val format = ExportKind.entries.firstOrNull { it.name.equals(proposed.format, ignoreCase = true) }
                    if (project == null || format == null || proposed.notes == null) null else Export(project.id, project.name, format, proposed.notes)
                }
                "open_project" -> projects.firstOrNull { it.id == proposed.projectId }?.let { OpenProject(it.id, it.name) }
                "go_to_projects" -> GoToProjects
                "open_notifications" -> OpenNotifications
                "open_account" -> OpenAccount
                else -> null
            }
        } catch (_: DateTimeParseException) {
            null
        }

        /** The API's names for reminder times. */
        private val LEADS = mapOf(
            "off" to ReminderLead.OFF,
            "at_start" to ReminderLead.AT_START,
            "15_min" to ReminderLead.MINUTES_15,
            "1_hour" to ReminderLead.HOUR_1,
            "1_day" to ReminderLead.DAY_1,
        )

        /** Null for a chip this app doesn't have, or a search for nothing. */
        private fun search(proposed: ProposedAction): Search? {
            val colors = proposed.colors.orEmpty().map { name -> TaskColor.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null }
            val kinds = proposed.kinds.orEmpty().map { name -> SearchKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null }
            val query = proposed.query?.trim()?.ifEmpty { null }
            if (query == null && colors.isEmpty() && kinds.isEmpty()) return null
            return Search(query, colors.toSet(), kinds.toSet())
        }
    }
}
