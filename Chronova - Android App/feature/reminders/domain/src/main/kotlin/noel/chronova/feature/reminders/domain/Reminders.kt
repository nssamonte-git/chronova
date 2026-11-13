package noel.chronova.feature.reminders.domain

import kotlinx.coroutines.flow.first
import noel.chronova.core.domain.reminders.ReminderSettingsQueries
import noel.chronova.core.domain.repository.ProjectQueries
import noel.chronova.core.model.ReminderLead
import noel.chronova.core.model.SchedulerTask

/** One task's reminder: due at [atMillis], [ReminderLead] before the task starts at [startMillis]. */
data class Reminder(
    val taskId: String,
    val projectId: String,
    val projectName: String,
    val title: String,
    val isMilestone: Boolean,
    val startMillis: Long,
    val atMillis: Long,
)

/** Which reminders exist, which are due now, and when the next one is. */
object ReminderPlanner {
    /** A reminder that comes later than this is dropped rather than shown (the phone was off, say). */
    const val MAX_LATE_MILLIS = 24 * 60 * 60_000L

    /** Every reminder for [tasks] (open tasks, of live projects) at [lead]; none when it is off. */
    fun reminders(tasks: List<SchedulerTask>, projectNames: Map<String, String>, lead: ReminderLead): List<Reminder> {
        val before = lead.beforeMillis ?: return emptyList()
        return tasks.filter { !it.isDone }.map { task ->
            Reminder(
                taskId = task.id,
                projectId = task.projectId,
                projectName = projectNames[task.projectId].orEmpty(),
                title = task.title,
                isMilestone = task.isMilestone,
                startMillis = task.startMillis,
                atMillis = task.startMillis - before,
            )
        }
    }

    /** Reminders whose time came after [sinceMillis] and by [nowMillis], and not too long ago to still be useful. */
    fun due(reminders: List<Reminder>, sinceMillis: Long, nowMillis: Long): List<Reminder> =
        reminders.filter { it.atMillis in (sinceMillis + 1)..nowMillis && nowMillis - it.atMillis <= MAX_LATE_MILLIS }
            .sortedBy { it.atMillis }

    /** When the next reminder is due after [nowMillis], or null when none is. */
    fun next(reminders: List<Reminder>, nowMillis: Long): Long? = reminders.filter { it.atMillis > nowMillis }.minOfOrNull { it.atMillis }
}

/** The one alarm that wakes the app for the next reminder (null cancels it). */
fun interface ReminderAlarm {
    fun set(atMillis: Long?)
}

/** Shows reminders on the phone. */
fun interface ReminderPoster {
    fun post(reminders: List<Reminder>)
}

/** When reminders were last handled (posted, or the plan was reset), so none is posted twice. */
interface ReminderLog {
    var handledUpToMillis: Long
}

/**
 * Keeps one alarm set for the next reminder and posts the due ones when it goes off.
 * Rescheduling (a task or the setting changed, sign-in, boot, a time change) starts the
 * plan afresh from now, so a reminder whose time has already passed is never posted
 * late for a task just added or moved. Signed out, there are no reminders.
 */
class ReminderEngine(
    private val projects: ProjectQueries,
    private val settings: ReminderSettingsQueries,
    private val signedIn: () -> Boolean,
    private val alarm: ReminderAlarm,
    private val poster: ReminderPoster,
    private val log: ReminderLog,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun reschedule() {
        val now = clock()
        log.handledUpToMillis = now
        alarm.set(if (signedIn()) ReminderPlanner.next(current(), now) else null)
    }

    /** The alarm went off (possibly late): post what came due since the last time, then set the next. */
    suspend fun onAlarm() {
        val now = clock()
        if (!signedIn()) {
            alarm.set(null)
            return
        }
        val reminders = current()
        val due = ReminderPlanner.due(reminders, log.handledUpToMillis, now)
        if (due.isNotEmpty()) poster.post(due)
        log.handledUpToMillis = now
        alarm.set(ReminderPlanner.next(reminders, now))
    }

    private suspend fun current(): List<Reminder> {
        val lead = settings.lead.value
        if (lead == ReminderLead.OFF) return emptyList()
        val names = projects.observeProjects().first().associate { it.project.id to it.project.name }
        return ReminderPlanner.reminders(projects.observeOpenTasks().first(), names, lead)
    }
}
