package noel.chronova.feature.assistant.presentation

import noel.chronova.core.model.ReminderLead
import noel.chronova.core.model.ThemeMode
import noel.chronova.core.ui.assistant.AssistantViewAction

/** One line of what the assistant proposes to change, as the orb shows it. Times are epoch millis. */
sealed interface ProposalLineUi {
    /** A task's times, title and/or progress change. */
    data class Changed(
        val titleBefore: String,
        val title: String,
        val startBefore: Long,
        val endBefore: Long,
        val start: Long,
        val end: Long,
        val progressBefore: Int = 0,
        val progress: Int = 0,
    ) : ProposalLineUi {
        val renamed: Boolean get() = titleBefore != title
        val moved: Boolean get() = startBefore != start || endBefore != end
        val progressed: Boolean get() = progressBefore != progress
    }

    /** A new task, or a new milestone ([start] equals [end]). */
    data class Added(val title: String, val start: Long, val end: Long) : ProposalLineUi {
        val isMilestone: Boolean get() = start == end
    }
    data class Removed(val title: String) : ProposalLineUi
    data class Linked(val predecessor: String, val successor: String) : ProposalLineUi
    data class Unlinked(val predecessor: String, val successor: String) : ProposalLineUi
}

/** A clash the proposal would cause, named under its lines. */
sealed interface WarningUi {
    /** [task] would overlap [other] in time. */
    data class Overlap(val task: String, val other: String) : WarningUi

    /** [successor] would start before [predecessor], which it waits for, ends. */
    data class BrokenLink(val predecessor: String, val successor: String) : WarningUi
}

/** Something the assistant asked the screen to show; [id] tells one from the next. */
data class ViewRequestUi(val id: Long, val action: AssistantViewAction)

/**
 * Something for the app's shell to do, once the assistant has said it: go to another screen, change
 * a setting, sync or export. [id] tells one request from the next.
 */
sealed interface AppRequestUi {
    val id: Long

    data class OpenProject(override val id: Long, val projectId: String) : AppRequestUi
    data class Projects(override val id: Long) : AppRequestUi
    data class Notifications(override val id: Long) : AppRequestUi
    data class Account(override val id: Long) : AppRequestUi
    data class Theme(override val id: Long, val theme: ThemeMode) : AppRequestUi
    data class Reminders(override val id: Long, val lead: ReminderLead) : AppRequestUi
    data class Sync(override val id: Long) : AppRequestUi

    /** [format] is the export's PDF, CSV or CALENDAR; [notes]: whether notes go in. */
    data class Export(override val id: Long, val projectId: String, val projectName: String, val format: String, val notes: Boolean) : AppRequestUi
}
