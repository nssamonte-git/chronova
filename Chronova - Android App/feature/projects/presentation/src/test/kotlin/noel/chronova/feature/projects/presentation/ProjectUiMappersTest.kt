package noel.chronova.feature.projects.presentation

import java.time.LocalDate
import java.time.ZoneOffset
import noel.chronova.core.domain.ProjectProblem
import noel.chronova.core.model.Project
import noel.chronova.core.model.ProjectSummary
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskOutline
import org.junit.Assert.assertEquals
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
    fun tasksBecomeBarsAsSharesOfTheProjectsDays() {
        val row = ProjectSummary(fourDays, taskCount = 2).toUi(
            listOf(
                TaskOutline("p1", start + day, start + 2 * day, TaskColor.GREEN),
                TaskOutline("p1", start + 3 * day, start + 3 * day, TaskColor.AMBER, isMilestone = true),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(
            listOf(
                ProjectBarUi(0.25f, 0.5f, TaskColor.GREEN),
                ProjectBarUi(0.75f, 0.75f, TaskColor.AMBER, milestone = true),
            ),
            row.bars,
        )
    }

    @Test
    fun aTaskOutsideTheProjectsDaysIsKeptWithinTheCard() {
        // Only possible through a bad sync record; the card must still draw.
        val row = ProjectSummary(fourDays, taskCount = 1).toUi(
            listOf(TaskOutline("p1", start - day, start + 6 * day, TaskColor.BLUE)),
            ZoneOffset.UTC,
        )
        assertEquals(listOf(ProjectBarUi(0f, 1f, TaskColor.BLUE)), row.bars)
    }

    @Test
    fun onlyTheFirstTasksAreDrawn() {
        val outlines = (0 until MAX_CARD_BARS + 3).map { TaskOutline("p1", start, start + day, TaskColor.entries[it % 4]) }
        val row = ProjectSummary(fourDays, taskCount = outlines.size).toUi(outlines, ZoneOffset.UTC)
        assertEquals(MAX_CARD_BARS, row.bars.size)
        assertEquals(outlines.take(MAX_CARD_BARS).map { it.color }, row.bars.map { it.color })
        assertEquals(outlines.size, row.taskCount) // the count still says how many there are
    }

    @Test
    fun theSharesFollowTheViewersZone() {
        // The same task is a quarter of the way in for a viewer in UTC, later for one further west.
        val outline = TaskOutline("p1", start + day, start + 2 * day, TaskColor.BLUE)
        val west = ProjectSummary(fourDays, taskCount = 1).toUi(listOf(outline), ZoneOffset.ofHours(-6))
        assertEquals(ProjectBarUi(0.1875f, 0.4375f, TaskColor.BLUE), west.bars.single())
    }

    @Test
    fun everyProblemKeepsItsDetail() {
        assertEquals(ProjectProblemUi.BlankName, ProjectProblem.BlankName.toUi())
        assertEquals(ProjectProblemUi.EndBeforeStart, ProjectProblem.EndBeforeStart.toUi())
        assertEquals(ProjectProblemUi.TooLong(366), ProjectProblem.TooLong(366).toUi())
        assertEquals(ProjectProblemUi.ExcludesTasks(4), ProjectProblem.ExcludesTasks(4).toUi())
    }
}
