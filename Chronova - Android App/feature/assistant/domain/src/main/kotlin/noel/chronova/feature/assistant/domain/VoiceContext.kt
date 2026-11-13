package noel.chronova.feature.assistant.domain

import noel.chronova.core.model.TimeScale
import noel.chronova.core.model.TaskColor
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import noel.chronova.core.domain.schedule.ScheduleState

/**
 * What the interpreter is told about the schedule: the project, today's date
 * and the tasks, all in the project's local time (`docs/voice-api.md`). Only
 * ids, titles, times and links — nothing else leaves the device.
 */
data class VoiceContext(
    val projectId: String,
    val projectName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val timeZone: String,
    val today: LocalDate,
    val now: LocalDateTime,
    val revision: String,
    val tasks: List<Task>,
    /** What the timeline shows, so "this task" or "zoom in" can be understood; null when unknown. */
    val view: ViewContext? = null,
) {
    data class Task(
        val id: String,
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val predecessorIds: List<String>,
        /** Percent done; never the task's notes, which stay on the phone. */
        val progress: Int = 0,
        val milestone: Boolean = false,
    )

    companion object {
        fun from(state: ScheduleState, zone: ZoneId, nowMillis: Long, view: ViewContext? = null): VoiceContext {
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
            val predecessors = state.links.groupBy({ it.successorId }, { it.predecessorId })
            return VoiceContext(
                projectId = state.project.id,
                projectName = state.project.name,
                startDate = state.project.startDate,
                endDate = state.project.endDate,
                // "Z" isn't a zone name servers accept; fixed offsets stay as "+08:00".
                timeZone = if (zone.normalized() == ZoneOffset.UTC) "UTC" else zone.id,
                today = now.toLocalDate(),
                now = now,
                revision = ScheduleRevision.of(state),
                tasks = state.tasks.map { task ->
                    Task(
                        id = task.id,
                        title = task.title,
                        start = local(task.startMillis, zone),
                        end = local(task.endMillis, zone),
                        predecessorIds = predecessors[task.id].orEmpty().sorted(),
                        progress = task.progress,
                        milestone = task.isMilestone,
                    )
                },
                // A selection whose task has gone (deleted meanwhile) isn't sent.
                view = view?.let { shown -> if (state.tasks.any { it.id == shown.selectedTaskId }) shown else shown.copy(selectedTaskId = null) },
            )
        }

        private fun local(millis: Long, zone: ZoneId) =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
    }
}

/** A short fingerprint of a schedule's content: equal schedules, equal revisions. */
object ScheduleRevision {
    fun of(state: ScheduleState): String {
        val text = buildString {
            append(state.project).append('\n')
            state.tasks.sortedBy { it.id }.forEach { append(it).append('\n') }
            state.links.sortedBy { it.id }.forEach { append(it).append('\n') }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}

/** What the timeline shows when a request is made (`context.view`). */
data class ViewContext(
    val scale: TimeScale,
    val criticalPath: Boolean,
    val selectedTaskId: String?,
    /** The open search, or null when none narrows the list. */
    val search: Search? = null,
) {
    data class Search(val query: String?, val colors: Set<TaskColor>, val kinds: Set<VoiceAction.SearchKind>)
}
