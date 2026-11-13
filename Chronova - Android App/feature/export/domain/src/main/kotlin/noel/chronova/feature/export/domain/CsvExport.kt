package noel.chronova.feature.export.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import noel.chronova.core.domain.schedule.ScheduleState
import noel.chronova.core.model.SchedulerTask

/**
 * A project as a spreadsheet (RFC 4180): one row per task in row order, local times in
 * [zone], CRLF line ends, and UTF-8 with a byte-order mark so spreadsheet apps read
 * accented titles correctly.
 */
object CsvExport {
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun build(schedule: ScheduleState, zone: ZoneId, includeNotes: Boolean): ByteArray {
        val titles = schedule.tasks.associate { it.id to it.title }
        val header = listOf("Title", "Start", "End", "Hours", "Milestone", "Progress (%)", "Depends on", "Colour") +
            if (includeNotes) listOf("Notes") else emptyList()
        val rows = schedule.tasks.sortedBy { it.sortOrder }.map { task ->
            val dependsOn = schedule.links.filter { it.successorId == task.id }.mapNotNull { titles[it.predecessorId] }
            listOf(
                task.title,
                local(task.startMillis, zone),
                local(task.endMillis, zone),
                hours(task),
                if (task.isMilestone) "Yes" else "No",
                task.progress.toString(),
                dependsOn.joinToString("; "),
                task.color.name.lowercase().replaceFirstChar { it.uppercase() },
            ) + if (includeNotes) listOf(task.notes) else emptyList()
        }
        val text = (listOf(header) + rows).joinToString("") { row -> row.joinToString(",") { quote(it) } + "\r\n" }
        return BOM + text.toByteArray(Charsets.UTF_8)
    }

    private fun local(millis: Long, zone: ZoneId) = time.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Hours with at most two decimals ("1.5", "0.25", "3"). */
    private fun hours(task: SchedulerTask): String =
        String.format(Locale.ROOT, "%.2f", task.durationMillis / 3_600_000.0).trimEnd('0').trimEnd('.')

    /** Quoted only when needed: a comma, quote, CR or LF, or space at either end. */
    internal fun quote(value: String): String {
        val needs = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || value != value.trim()
        return if (needs) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
}
