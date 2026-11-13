package noel.chronova.feature.export.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import noel.chronova.core.domain.schedule.ScheduleState

/**
 * A project as an iCalendar file (RFC 5545) for calendar apps: one event per task, in
 * UTC, with a stable UID per task (re-importing updates rather than duplicates in apps
 * that honour it). A milestone is an event with no length (no DTEND). Text is escaped,
 * lines end in CRLF and are folded at 75 octets without splitting a character.
 */
object IcsExport {
    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    fun build(schedule: ScheduleState, includeNotes: Boolean, nowMillis: Long): ByteArray {
        val titles = schedule.tasks.associate { it.id to it.title }
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Chronova//Chronova//EN",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:" + escape(schedule.project.name),
        )
        for (task in schedule.tasks.sortedBy { it.sortOrder }) {
            val dependsOn = schedule.links.filter { it.successorId == task.id }.mapNotNull { titles[it.predecessorId] }
            val description = buildList {
                add("Progress: ${task.progress}%")
                if (dependsOn.isNotEmpty()) add("Depends on: " + dependsOn.joinToString(", "))
                if (includeNotes && task.notes.isNotBlank()) add("\n" + task.notes)
            }.joinToString("\n")
            lines += "BEGIN:VEVENT"
            lines += "UID:${task.id}@chronova"
            lines += "DTSTAMP:" + stamp(nowMillis)
            lines += "DTSTART:" + stamp(task.startMillis)
            if (!task.isMilestone) lines += "DTEND:" + stamp(task.endMillis)
            lines += "SUMMARY:" + escape(task.title)
            lines += "DESCRIPTION:" + escape(description)
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("") { fold(it) + "\r\n" }.toByteArray(Charsets.UTF_8)
    }

    private fun stamp(millis: Long): String = utc.format(Instant.ofEpochMilli(millis))

    /** TEXT escaping: backslash, semicolon, comma and line breaks. */
    internal fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

    /** Content lines over 75 octets continue on lines starting with a space; a character is never split. */
    internal fun fold(line: String): String {
        val out = StringBuilder()
        var octets = 0
        var limit = MAX_OCTETS
        var i = 0
        while (i < line.length) {
            val codePoint = line.codePointAt(i)
            val chars = Character.charCount(codePoint)
            val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (octets + size > limit) {
                out.append("\r\n ")
                octets = 0
                limit = MAX_OCTETS - 1 // the leading space counts
            }
            out.appendCodePoint(codePoint)
            octets += size
            i += chars
        }
        return out.toString()
    }

    private const val MAX_OCTETS = 75
}
