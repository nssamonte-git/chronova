package noel.chronova.feature.assistant.presentation

import noel.chronova.core.domain.ProjectProblem
import noel.chronova.feature.assistant.domain.ProjectPlan
import noel.chronova.core.model.ReminderLead
import noel.chronova.core.model.ThemeMode
import noel.chronova.core.model.TimeScale
import java.time.format.DateTimeFormatter
import noel.chronova.feature.assistant.domain.VoiceAction
import android.content.res.Resources
import java.time.ZoneId
import noel.chronova.feature.assistant.domain.VoiceApiException
import noel.chronova.core.domain.TaskProblem
import noel.chronova.feature.assistant.domain.schedule.CommandProblem
import noel.chronova.core.ui.TimeFormat

/** What the assistant says (and captions) in its own words, outside the server's explanations. */
interface AssistantPhrases {
    fun greeting(): String

    /** Said once, the first time the app is opened: who the assistant is and how to use it. */
    fun introduction(): String

    /** Said each time the app is opened after that. */
    fun welcome(): String

    /** After the clash-free alternative was turned down: whether to apply the change as asked. */
    fun applyAsAsked(): String

    /** A yes was heard but only faintly, maybe from across the room. */
    fun wasThatAYes(): String
    fun nothingHeard(): String
    fun goodbye(): String
    fun nothingChanged(): String
    fun notUnderstood(): String
    fun unsupported(reason: String?): String
    fun nothingToChange(): String
    fun scheduleChanged(): String
    fun microphoneUnavailable(): String
    fun failure(kind: VoiceApiException.Kind): String
    fun cannotDo(problem: CommandProblem, titles: Map<String, String>, projectEndMillis: Long): String

    /** What it says after doing [action] (the last one, when there were several). */
    fun showing(action: VoiceAction): String

    /** Asked to undo, but it has changed nothing (or its change was already undone). */
    fun nothingToUndo(): String

    /** Asks before making a project: what it will be called and when it runs. */
    fun askNewProject(plan: ProjectPlan, name: String): String

    /** The name a copy gets when none was said ("Copy of Jnn", as "Duplicate" names it). */
    fun copyName(sourceName: String): String

    fun projectCreated(name: String): String

    fun projectProblem(problem: ProjectProblem): String
}

/** [AssistantPhrases] from the app's string resources. */
class ResourcePhrases(private val resources: Resources, private val zone: ZoneId = ZoneId.systemDefault()) : AssistantPhrases {
    override fun greeting() = resources.getString(R.string.assistant_greeting)
    override fun introduction() = resources.getString(R.string.assistant_introduction)
    override fun welcome() = resources.getString(R.string.assistant_welcome)
    override fun applyAsAsked() = resources.getString(R.string.assistant_apply_as_asked)
    override fun wasThatAYes() = resources.getString(R.string.assistant_was_that_a_yes)
    override fun nothingHeard() = resources.getString(R.string.assistant_say_again)
    override fun goodbye() = resources.getString(R.string.assistant_goodbye)
    override fun nothingChanged() = resources.getString(R.string.assistant_nothing_changed)
    override fun notUnderstood() = resources.getString(R.string.assistant_not_understood)
    override fun unsupported(reason: String?) = reason ?: resources.getString(R.string.assistant_unsupported)
    override fun nothingToChange() = resources.getString(R.string.assistant_nothing_to_change)
    override fun scheduleChanged() = resources.getString(R.string.assistant_schedule_changed)
    override fun microphoneUnavailable() = resources.getString(R.string.assistant_mic_unavailable)

    override fun failure(kind: VoiceApiException.Kind) = resources.getString(
        when (kind) {
            VoiceApiException.Kind.UNAUTHORIZED -> R.string.assistant_signed_out
            VoiceApiException.Kind.DISABLED -> R.string.assistant_disabled
            VoiceApiException.Kind.RATE_LIMITED -> R.string.assistant_rate_limited
            VoiceApiException.Kind.NETWORK -> R.string.assistant_offline
            VoiceApiException.Kind.TOO_LONG -> R.string.assistant_too_long
            VoiceApiException.Kind.UNAVAILABLE, VoiceApiException.Kind.REJECTED -> R.string.assistant_unavailable
        },
    )

    override fun nothingToUndo() = resources.getString(R.string.assistant_nothing_to_undo)

    override fun askNewProject(plan: ProjectPlan, name: String): String = when (plan) {
        is ProjectPlan.Create -> resources.getString(R.string.assistant_ask_create_project, name, plan.startDate.format(SPOKEN_DAY), plan.endDate.format(SPOKEN_DAY))
        is ProjectPlan.Copy -> resources.getString(R.string.assistant_ask_copy_project, plan.sourceName, name, plan.startDate.format(SPOKEN_DAY), plan.endDate.format(SPOKEN_DAY))
    }

    override fun copyName(sourceName: String) = resources.getString(R.string.assistant_copy_name, sourceName)

    override fun projectCreated(name: String) = resources.getString(R.string.assistant_project_ready, name)

    override fun projectProblem(problem: ProjectProblem): String = when (problem) {
        ProjectProblem.BlankName -> resources.getString(R.string.assistant_project_needs_name)
        ProjectProblem.EndBeforeStart -> resources.getString(R.string.assistant_project_ends_before_start)
        is ProjectProblem.TooLong -> resources.getQuantityString(R.plurals.assistant_project_too_long, problem.maxDays, problem.maxDays)
        is ProjectProblem.ExcludesTasks -> resources.getString(R.string.assistant_schedule_changed)
    }

    override fun showing(action: VoiceAction): String = when (action) {
        VoiceAction.GoToToday -> resources.getString(R.string.assistant_showing_today)
        is VoiceAction.GoToDate -> resources.getString(R.string.assistant_showing_day, action.date.format(SPOKEN_DAY))
        is VoiceAction.ShowTask -> resources.getString(R.string.assistant_showing_task, action.title)
        is VoiceAction.SetScale -> resources.getString(
            when (action.scale) {
                TimeScale.HOURS -> R.string.assistant_showing_hours
                TimeScale.DAYS -> R.string.assistant_showing_days
                TimeScale.WEEKS -> R.string.assistant_showing_weeks
                TimeScale.MONTHS -> R.string.assistant_showing_months
            },
        )
        is VoiceAction.Zoom -> resources.getString(
            when (action.step) {
                VoiceAction.ZoomStep.IN -> R.string.assistant_zoomed_in
                VoiceAction.ZoomStep.OUT -> R.string.assistant_zoomed_out
                VoiceAction.ZoomStep.RESET -> R.string.assistant_zoom_reset
            },
        )
        is VoiceAction.RowHeight -> resources.getString(if (action.bigger) R.string.assistant_rows_bigger else R.string.assistant_rows_smaller)
        is VoiceAction.CriticalPath -> resources.getString(if (action.on) R.string.assistant_critical_path_on else R.string.assistant_critical_path_off)
        is VoiceAction.Search -> resources.getString(R.string.assistant_showing_matches)
        VoiceAction.ClearSearch -> resources.getString(R.string.assistant_showing_all)
        is VoiceAction.OpenTask -> resources.getString(R.string.assistant_opening_task, action.title)
        is VoiceAction.OpenProject -> resources.getString(R.string.assistant_opening_project, action.name)
        VoiceAction.GoToProjects -> resources.getString(R.string.assistant_showing_projects)
        VoiceAction.OpenNotifications -> resources.getString(R.string.assistant_showing_notifications)
        VoiceAction.OpenAccount -> resources.getString(R.string.assistant_showing_account)
        is VoiceAction.SetTheme -> resources.getString(
            when (action.theme) {
                ThemeMode.SYSTEM -> R.string.assistant_theme_system
                ThemeMode.LIGHT -> R.string.assistant_theme_light
                ThemeMode.DARK -> R.string.assistant_theme_dark
            },
        )
        is VoiceAction.SetReminders -> resources.getString(
            when (action.lead) {
                ReminderLead.OFF -> R.string.assistant_reminders_off
                ReminderLead.AT_START -> R.string.assistant_reminders_at_start
                ReminderLead.MINUTES_15 -> R.string.assistant_reminders_15_min
                ReminderLead.HOUR_1 -> R.string.assistant_reminders_1_hour
                ReminderLead.DAY_1 -> R.string.assistant_reminders_1_day
            },
        )
        VoiceAction.SyncNow -> resources.getString(R.string.assistant_syncing)
        is VoiceAction.Export -> resources.getString(
            when (action.format) {
                VoiceAction.ExportKind.PDF -> R.string.assistant_exporting_pdf
                VoiceAction.ExportKind.CSV -> R.string.assistant_exporting_csv
                VoiceAction.ExportKind.CALENDAR -> R.string.assistant_exporting_calendar
            },
            action.projectName,
        )
    }

    override fun cannotDo(problem: CommandProblem, titles: Map<String, String>, projectEndMillis: Long): String {
        fun title(id: String) = titles[id] ?: id
        return when (problem) {
            is CommandProblem.UnknownTask -> resources.getString(R.string.assistant_unknown_task)
            is CommandProblem.InvalidTask -> when (val rule = problem.problem) {
                TaskProblem.BlankTitle -> resources.getString(R.string.assistant_task_blank_title)
                TaskProblem.EndNotAfterStart -> resources.getString(R.string.assistant_task_end_before_start, problem.title)
                is TaskProblem.TooShort -> resources.getQuantityString(R.plurals.assistant_task_too_short, rule.minMinutes, problem.title, rule.minMinutes)
                TaskProblem.OutsideProject -> resources.getString(R.string.assistant_task_outside, problem.title)
                is TaskProblem.NotesTooLong -> resources.getQuantityString(R.plurals.assistant_task_notes_too_long, rule.maxLength, problem.title, rule.maxLength)
                TaskProblem.BadProgress -> resources.getString(R.string.assistant_task_bad_progress, problem.title)
            }
            is CommandProblem.WouldCycle -> resources.getString(R.string.assistant_would_cycle, title(problem.predecessorId), title(problem.successorId))
            is CommandProblem.AlreadyLinked -> resources.getString(R.string.assistant_already_linked, title(problem.predecessorId), title(problem.successorId))
            is CommandProblem.NotLinked -> resources.getString(R.string.assistant_not_linked, title(problem.predecessorId), title(problem.successorId))
            is CommandProblem.MilestoneHasNoLength -> resources.getString(R.string.assistant_milestone_no_length, problem.title)
            is CommandProblem.DoesNotFit -> resources.getString(R.string.assistant_does_not_fit, TimeFormat.day(projectEndMillis - 1, zone))
            is CommandProblem.Rejected -> resources.getString(R.string.assistant_schedule_changed)
        }
    }
}

/** A day as it is said: "Friday 20 November". */
private val SPOKEN_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
