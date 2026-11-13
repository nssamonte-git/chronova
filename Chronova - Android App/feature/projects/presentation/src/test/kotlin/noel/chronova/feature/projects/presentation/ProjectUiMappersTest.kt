package noel.chronova.feature.projects.presentation

import java.time.LocalDate
import java.time.ZoneOffset
import noel.chronova.core.domain.ProjectProblem
import noel.chronova.core.model.Project
import noel.chronova.core.model.ProjectSummary
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskOutline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectUiMappersTest {
    @Test
    fun summaryBecomesARow() {
        val project = Project("p1", "Launch", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9))
        assertEquals(
            ProjectRowUi("p1", "Launch", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9), taskCount = 3),
            ProjectSummary(project, taskCount = 3).toUi(),
        )
    }

    private val day = 24 * 60 * 60 * 1000L

    /** A four-day project, 2 to 5 March 2026, and the UTC millis of its first midnight. */
    private val fourDays = Project("p1", "Launch", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 5))
    private val start = LocalDate.of(2026, 3, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun aFewTasksGetARowEachAsSharesOfTheProjectsDays() {
        val row = ProjectSummary(fourDays, taskCount = 2).toUi(
            listOf(
                TaskOutline("p1", start + day, start + 2 * day, TaskColor.GREEN),
                TaskOutline("p1", start + 3 * day, start + 3 * day, TaskColor.AMBER, isMilestone = true),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(
            listOf(
                listOf(ProjectBarUi(0.25f, 0.5f, TaskColor.GREEN)),
                // A milestone is a dot at its moment; its length is only the room it takes.
                listOf(ProjectBarUi(0.75f, 0.75f + MIN_BAR_SHARE, TaskColor.AMBER, milestone = true)),
            ),
            row.lanes,
        )
    }

    @Test
    fun aFewTasksKeepTheirRowOrderEvenWhenALaterRowStartsEarlier() {
        val row = ProjectSummary(fourDays, taskCount = 2).toUi(
            listOf(
                TaskOutline("p1", start + 2 * day, start + 3 * day, TaskColor.PURPLE),
                TaskOutline("p1", start, start + day, TaskColor.BLUE),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(listOf(TaskColor.PURPLE, TaskColor.BLUE), row.lanes.map { it.single().color })
    }

    @Test
    fun aTaskOutsideTheProjectsDaysIsKeptWithinTheCard() {
        // Only possible through a bad sync record; the card must still draw.
        val row = ProjectSummary(fourDays, taskCount = 1).toUi(
            listOf(TaskOutline("p1", start - day, start + 6 * day, TaskColor.BLUE)),
            ZoneOffset.UTC,
        )
        assertEquals(listOf(listOf(ProjectBarUi(0f, 1f, TaskColor.BLUE))), row.lanes)
    }

    @Test
    fun aVeryShortTaskIsLongEnoughToSee() {
        // An hour of a four-day project is about 1% of it.
        val hour = day / 24
        val early = ProjectSummary(fourDays, taskCount = 1).toUi(listOf(TaskOutline("p1", start + day, start + day + hour, TaskColor.BLUE)), ZoneOffset.UTC)
        assertEquals(ProjectBarUi(0.25f, 0.25f + MIN_BAR_SHARE, TaskColor.BLUE), early.lanes.single().single())
        // At the very end it is moved back rather than cut off.
        val last = ProjectSummary(fourDays, taskCount = 1).toUi(listOf(TaskOutline("p1", start + 4 * day - hour, start + 4 * day, TaskColor.BLUE)), ZoneOffset.UTC)
        assertEquals(ProjectBarUi(1f - MIN_BAR_SHARE, 1f, TaskColor.BLUE), last.lanes.single().single())
    }

    @Test
    fun aHundredTasksArePackedIntoAFewRowsWithNoneLeftOut() {
        // A hundred-day project with a one-day task each day, one after the other.
        val hundredDays = Project("p1", "Year", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 6, 9))
        val outlines = (0 until 100).map { TaskOutline("p1", start + it * day, start + (it + 1) * day, TaskColor.entries[it % 4]) }
        val row = ProjectSummary(hundredDays, taskCount = 100).toUi(outlines, ZoneOffset.UTC)

        // Each bar is 3% long once it can be seen, so it takes at least three rows; never more than the card has.
        assertTrue(row.lanes.size in 3..MAX_CARD_LANES)
        assertEquals(100, row.lanes.sumOf { it.size })
        // Every row runs from left to right.
        row.lanes.forEach { lane -> lane.zipWithNext { a, b -> assertTrue(a.start <= b.start) } }
        // The chart reaches from the first day to the last.
        assertEquals(0f, row.lanes.flatten().minOf { it.start }, 0f)
        assertEquals(1f, row.lanes.flatten().maxOf { it.end }, 0f)
    }

    @Test
    fun moreOverlappingTasksThanRowsShareARowRatherThanDisappear() {
        // Eight tasks that all last the whole project.
        val outlines = (0 until 8).map { TaskOutline("p1", start, start + 4 * day, TaskColor.entries[it % 4]) }
        val row = ProjectSummary(fourDays, taskCount = 8).toUi(outlines, ZoneOffset.UTC)
        assertEquals(MAX_CARD_LANES, row.lanes.size)
        assertEquals(8, row.lanes.sumOf { it.size })
    }

    @Test
    fun packingPutsABarInTheFirstRowThatIsFree() {
        val a = ProjectBarUi(0f, 0.4f, TaskColor.BLUE)
        val b = ProjectBarUi(0.2f, 0.6f, TaskColor.GREEN)
        val c = ProjectBarUi(0.4f, 0.8f, TaskColor.PURPLE) // the first row is free again exactly here
        val d = ProjectBarUi(0.7f, 0.9f, TaskColor.AMBER)
        assertEquals(listOf(listOf(a, c), listOf(b, d)), packIntoLanes(listOf(d, c, b, a)))
    }

    @Test
    fun theSharesFollowTheViewersZone() {
        // The same task is a quarter of the way in for a viewer in UTC, later for one further west.
        val outline = TaskOutline("p1", start + day, start + 2 * day, TaskColor.BLUE)
        val west = ProjectSummary(fourDays, taskCount = 1).toUi(listOf(outline), ZoneOffset.ofHours(-6))
        assertEquals(ProjectBarUi(0.1875f, 0.4375f, TaskColor.BLUE), west.lanes.single().single())
    }

    @Test
    fun everyProblemKeepsItsDetail() {
        assertEquals(ProjectProblemUi.BlankName, ProjectProblem.BlankName.toUi())
        assertEquals(ProjectProblemUi.EndBeforeStart, ProjectProblem.EndBeforeStart.toUi())
        assertEquals(ProjectProblemUi.TooLong(366), ProjectProblem.TooLong(366).toUi())
        assertEquals(ProjectProblemUi.ExcludesTasks(4), ProjectProblem.ExcludesTasks(4).toUi())
    }
}
