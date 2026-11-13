package noel.chronova.feature.export.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import noel.chronova.core.domain.schedule.ScheduleState
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.testing.FakeProjectRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {
    private val zone = ZoneId.of("Asia/Manila") // UTC+8, so local and UTC times differ
    private val project = Project("p1", "Office move", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6))

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val pack = SchedulerTask("t1", "p1", "Pack, label \"boxes\"", at(2, 9), at(2, 10, 30), TaskColor.GREEN, 0, progress = 40, notes = "Tape; markers\nand crates")
    private val move = SchedulerTask("t2", "p1", "Déménagement 🚚", at(3, 8), at(3, 17), TaskColor.BLUE, 1)
    private val keys = SchedulerTask("t3", "p1", "Hand over keys", at(6, 12), at(6, 12), TaskColor.AMBER, 2, progress = 100, isMilestone = true)
    private val schedule = ScheduleState(
        project,
        listOf(move, pack, keys),
        listOf(TaskDependency("l1", "p1", "t1", "t2"), TaskDependency("l2", "p1", "t2", "t3")),
    )

    private fun csv(includeNotes: Boolean = true) = CsvExport.build(schedule, zone, includeNotes)
    private fun ics(includeNotes: Boolean = true) = IcsExport.build(schedule, includeNotes, at(1, 8)).toString(Charsets.UTF_8)

    @Test
    fun csvHasABomAHeaderAndOneQuotedRowPerTaskInRowOrder() {
        val bytes = csv()
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOfRange(0, 3))
        val text = bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        assertTrue(text.endsWith("\r\n"))
        val lines = text.split("\r\n")
        assertEquals("Title,Start,End,Hours,Milestone,Progress (%),Depends on,Colour,Notes", lines[0])
        // The note's line break (LF) stays inside its quotes; rows end in CRLF.
        assertEquals("\"Pack, label \"\"boxes\"\"\",2026-11-02 09:00,2026-11-02 10:30,1.5,No,40,,Green,\"Tape; markers\nand crates\"", lines[1])
        assertEquals("Déménagement 🚚,2026-11-03 08:00,2026-11-03 17:00,9,No,0,\"Pack, label \"\"boxes\"\"\",Blue,", lines[2])
        assertEquals("Hand over keys,2026-11-06 12:00,2026-11-06 12:00,0,Yes,100,Déménagement 🚚,Amber,", lines[3])
    }

    @Test
    fun csvWithoutNotesHasNoNotesColumn() {
        val text = csv(includeNotes = false).toString(Charsets.UTF_8)
        assertFalse(text.contains("Notes"))
        assertFalse(text.contains("markers"))
    }

    @Test
    fun icsHasOneEventPerTaskInUtcAndAMilestoneHasNoEnd() {
        val text = ics()
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
        assertEquals(3, Regex("BEGIN:VEVENT").findAll(text).count())
        // 09:00 in Manila is 01:00 UTC.
        assertTrue(text.contains("UID:t1@chronova\r\nDTSTAMP:20261101T000000Z\r\nDTSTART:20261102T010000Z\r\nDTEND:20261102T023000Z\r\n"))
        val milestone = text.substringAfter("UID:t3@chronova").substringBefore("END:VEVENT")
        assertTrue(milestone.contains("DTSTART:20261106T040000Z"))
        assertFalse(milestone.contains("DTEND"))
        assertTrue(text.contains("X-WR-CALNAME:Office move\r\n"))
    }

    @Test
    fun icsEscapesTextAndKeepsNotesOnlyWhenAsked() {
        val text = ics().replace("\r\n ", "") // unfold
        assertTrue(text.contains("SUMMARY:Pack\\, label \"boxes\"\r\n"))
        assertTrue(text.contains("DESCRIPTION:Progress: 40%\\n\\nTape\\; markers\\nand crates\r\n"))
        assertTrue(text.contains("DESCRIPTION:Progress: 0%\\nDepends on: Pack\\, label \"boxes\"\r\n"))
        assertFalse(ics(includeNotes = false).contains("markers"))
    }

    @Test
    fun longLinesFoldAt75OctetsWithoutSplittingACharacter() {
        val line = "SUMMARY:" + "é".repeat(40) + "🚚".repeat(10)
        val folded = IcsExport.fold(line)
        val parts = folded.split("\r\n")
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        assertTrue(parts.drop(1).all { it.startsWith(" ") })
        assertEquals(line, parts.first() + parts.drop(1).joinToString("") { it.drop(1) })
        assertEquals("SHORT", IcsExport.fold("SHORT"))
    }

    @Test
    fun exportReadsTheScheduleNamesTheFileAndSavesIt() = runTest {
        val repository = FakeProjectRepository(listOf(project), schedule.tasks).also { it.dependencies.value = schedule.links }
        val saved = mutableListOf<Pair<String, String>>()
        val export = ExportProject(
            repository,
            pdf = { _, _, _ -> byteArrayOf(1, 2, 3) },
            files = { name, mime, _ -> saved += name to mime; ExportedFile(name, mime, "content://x/$name") },
            zone = zone,
            clock = { at(1, 8) },
        )
        assertEquals(ExportResult.Ready(ExportedFile("Office move.pdf", "application/pdf", "content://x/Office move.pdf")), export("p1", ExportFormat.PDF, true))
        export("p1", ExportFormat.CSV, false)
        export("p1", ExportFormat.CALENDAR, true)
        assertEquals(listOf("Office move.pdf" to "application/pdf", "Office move.csv" to "text/csv", "Office move.ics" to "text/calendar"), saved)
        assertEquals(ExportResult.ProjectGone, export("gone", ExportFormat.CSV, true))
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("Q3- launch-plan.csv", ExportProject.fileName("Q3/ launch:plan", ExportFormat.CSV))
        assertEquals("project.ics", ExportProject.fileName("///", ExportFormat.CALENDAR))
        assertEquals("project.pdf", ExportProject.fileName("   ", ExportFormat.PDF))
    }
}
