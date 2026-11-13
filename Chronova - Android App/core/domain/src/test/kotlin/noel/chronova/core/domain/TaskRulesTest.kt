package noel.chronova.core.domain

import noel.chronova.core.model.TimelineWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskRulesTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val window = TimelineWindow(0L, 24 * hour)

    @Test
    fun validTaskHasNoProblem() {
        assertNull(TaskRules.validate("Design", 9 * hour, 10 * hour, window))
        // The minimum and the window edges are inclusive.
        assertNull(TaskRules.validate("Design", 0L, 15 * minute, window))
        assertNull(TaskRules.validate("Design", 23 * hour, 24 * hour, window))
    }

    @Test
    fun problemsAreReportedInOrder() {
        assertEquals(TaskProblem.BlankTitle, TaskRules.validate(" ", 10 * hour, 9 * hour, window))
        assertEquals(TaskProblem.EndNotAfterStart, TaskRules.validate("A", 10 * hour, 10 * hour, window))
        assertEquals(TaskProblem.TooShort(15), TaskRules.validate("A", 10 * hour, 10 * hour + 14 * minute, window))
        assertEquals(TaskProblem.OutsideProject, TaskRules.validate("A", 23 * hour, 25 * hour, window))
        assertEquals(TaskProblem.OutsideProject, TaskRules.validate("A", -hour, hour, window))
    }

    @Test
    fun notesAndProgressHaveLimits() {
        assertNull(TaskRules.validate("A", 9 * hour, 10 * hour, window, progress = 100, notes = "x".repeat(2000)))
        assertEquals(TaskProblem.NotesTooLong(2000), TaskRules.validate("A", 9 * hour, 10 * hour, window, notes = "x".repeat(2001)))
        assertEquals(TaskProblem.BadProgress, TaskRules.validate("A", 9 * hour, 10 * hour, window, progress = 101))
        assertEquals(TaskProblem.BadProgress, TaskRules.validate("A", 9 * hour, 10 * hour, window, progress = -1))
    }

    @Test
    fun aMilestoneIsAMomentInsideTheProject() {
        assertNull(TaskRules.validate("Launch", 9 * hour, 9 * hour, window, isMilestone = true))
        assertNull(TaskRules.validate("Launch", 0L, 0L, window, isMilestone = true, progress = 100))
        // It has no length, so it can't end after it starts, and the project's last midnight is outside.
        assertEquals(TaskProblem.EndNotAfterStart, TaskRules.validate("Launch", 9 * hour, 10 * hour, window, isMilestone = true))
        assertEquals(TaskProblem.OutsideProject, TaskRules.validate("Launch", 24 * hour, 24 * hour, window, isMilestone = true))
        assertEquals(TaskProblem.OutsideProject, TaskRules.validate("Launch", -hour, -hour, window, isMilestone = true))
        assertEquals(TaskProblem.BadProgress, TaskRules.validate("Launch", 9 * hour, 9 * hour, window, isMilestone = true, progress = 50))
    }
}
