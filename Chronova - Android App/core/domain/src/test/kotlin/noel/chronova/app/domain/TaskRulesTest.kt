package noel.chronova.app.domain

import noel.chronova.app.domain.model.TimelineWindow
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
}
