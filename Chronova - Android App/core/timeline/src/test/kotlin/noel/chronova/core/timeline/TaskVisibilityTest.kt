package noel.chronova.core.timeline

import noel.chronova.core.timeline.Fixtures.at
import noel.chronova.core.timeline.Fixtures.origin
import noel.chronova.core.timeline.Fixtures.task
import noel.chronova.core.model.SchedulerTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskVisibilityTest {
    private val pxPerMinute = 192f / 60f // the phone's default Hours zoom: 192 px per hour

    private val morning = task(9, 0, 10, 0).copy(id = "morning")
    private val noon = task(11, 30, 13, 0).copy(id = "noon")
    private val evening = task(18, 0, 19, 0).copy(id = "evening")
    private val tasks = listOf(morning, noon, evening)

    private fun onScreen(visible: TimeSpan?, keep: Set<String> = emptySet()): List<SchedulerTask> =
        TaskVisibility.onScreen(tasks, visible, keep, id = { it.id }, start = { it.startMillis }, end = { it.endMillis })

    @Test
    fun theVisibleSpanFollowsTheScrollAndWidth() {
        // Scrolled to 09:00 with 2.5 hours of lane visible.
        val span = TaskVisibility.visibleSpan(scrollX = 9 * 192f, viewportWidth = 2.5f * 192f, originMillis = origin, pxPerMinute = pxPerMinute)
        assertEquals(TimeSpan(at(9, 0), at(11, 30)), span)
    }

    @Test
    fun beforeLayoutThereIsNoSpanAndEveryTaskIsListed() {
        assertNull(TaskVisibility.visibleSpan(scrollX = 0f, viewportWidth = 0f, originMillis = origin, pxPerMinute = pxPerMinute))
        assertEquals(tasks, onScreen(visible = null))
    }

    @Test
    fun onlyTasksOverlappingTheScreenAreListedInTheirOrder() {
        assertEquals(listOf(morning, noon), onScreen(TimeSpan(at(9, 30), at(12, 0))))
        assertEquals(listOf(evening), onScreen(TimeSpan(at(17, 0), at(20, 0))))
        assertEquals(emptyList<Any>(), onScreen(TimeSpan(at(14, 0), at(16, 0))))
    }

    @Test
    fun aTaskThatOnlyTouchesTheEdgeIsNotListed() {
        // Morning ends at 10:00 and noon starts at 11:30: neither overlaps 10:00–11:30.
        assertEquals(emptyList<Any>(), onScreen(TimeSpan(at(10, 0), at(11, 30))))
    }

    @Test
    fun keptTasksStayListedWhereverTheyAre() {
        assertEquals(listOf(morning, evening), onScreen(TimeSpan(at(17, 0), at(20, 0)), keep = setOf("morning")))
    }
}
