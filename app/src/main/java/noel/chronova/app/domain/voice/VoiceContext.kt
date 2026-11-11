package noel.chronova.app.domain.voice

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import noel.chronova.app.domain.schedule.ScheduleState

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
) {
    data class Task(
        val id: String,
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val predecessorIds: List<String>,
    )

    companion object {
        fun from(state: ScheduleState, zone: ZoneId, nowMillis: Long): VoiceContext {
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
                    )
                },
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
