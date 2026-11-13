package noel.chronova.feature.timeline.presentation

import noel.chronova.core.model.TaskColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFilterTest {
    private val design = TaskUi("d", "UI Design", 0, 1, TaskColor.BLUE, progress = 100)
    private val build = TaskUi("b", "Build API", 0, 1, TaskColor.GREEN, progress = 40)
    private val launch = TaskUi("l", "Launch", 1, 1, TaskColor.AMBER, isMilestone = true)
    private val all = listOf(design, build, launch)

    private fun ids(filter: TaskFilter) = all.filter(filter::matches).map { it.id }

    @Test
    fun anEmptyFilterMatchesEverythingAndIsNotActive() {
        assertFalse(TaskFilter().isActive)
        assertFalse(TaskFilter(query = "   ").isActive)
        assertEquals(listOf("d", "b", "l"), ids(TaskFilter()))
    }

    @Test
    fun theQueryMatchesTitlesInAnyCase() {
        assertEquals(listOf("d"), ids(TaskFilter(query = "design")))
        assertEquals(listOf("b"), ids(TaskFilter(query = " API ")))
        assertEquals(emptyList<String>(), ids(TaskFilter(query = "review")))
    }

    @Test
    fun chipsOfOneKindWidenAndKindsNarrow() {
        assertEquals(listOf("d", "b"), ids(TaskFilter(colors = setOf(TaskColor.BLUE, TaskColor.GREEN))))
        assertEquals(listOf("d", "l"), ids(TaskFilter(kinds = setOf(TaskKind.DONE, TaskKind.MILESTONE))))
        assertEquals(listOf("b"), ids(TaskFilter(colors = setOf(TaskColor.BLUE, TaskColor.GREEN), kinds = setOf(TaskKind.NOT_DONE))))
        assertEquals(listOf("b"), ids(TaskFilter(query = "a", colors = setOf(TaskColor.GREEN))))
        assertTrue(TaskFilter(kinds = setOf(TaskKind.MILESTONE)).isActive)
    }
}
