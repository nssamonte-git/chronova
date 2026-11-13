package noel.chronova.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTaskTest {
    private fun task(start: Long = 0, end: Long = 1_000, progress: Int = 0, milestone: Boolean = false) =
        SchedulerTask("t1", "p1", "Build", start, end, progress = progress, isMilestone = milestone)

    @Test
    fun aTaskIsDoneAtOneHundredPercent() {
        assertFalse(task(progress = 99).isDone)
        assertTrue(task(progress = 100).isDone)
    }

    @Test
    fun aMilestoneHasNoLengthAndIsDoneOrNot() {
        assertEquals(0L, task(end = 0, milestone = true).durationMillis)
        assertTrue(task(end = 0, progress = 100, milestone = true).isDone)
        assertThrows(IllegalArgumentException::class.java) { task(end = 1_000, milestone = true) }
        assertThrows(IllegalArgumentException::class.java) { task(end = 0, progress = 50, milestone = true) }
    }

    @Test
    fun otherTasksStillEndAfterTheyStart() {
        assertThrows(IllegalArgumentException::class.java) { task(end = 0) }
        assertThrows(IllegalArgumentException::class.java) { task(progress = 101) }
        assertThrows(IllegalArgumentException::class.java) { task(progress = -1) }
    }
}
