package noel.chronova.app.presentation

import java.time.ZoneOffset
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.model.TimeScale
import noel.chronova.app.domain.model.TimelineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChronovaViewModelTest {

    private val zone = ZoneOffset.UTC
    private val viewModel = ChronovaViewModel(zone)

    private fun at(hour: Int, minute: Int = 0): Long =
        SampleTasks.REFERENCE_DATE.atTime(hour, minute).toInstant(zone).toEpochMilli()

    @Test
    fun initialState_hasSampleTasksAtSpecifiedTimes() {
        val tasks = viewModel.state.value.tasks
        assertEquals(listOf("UI Design", "Implementation", "QA Testing", "Review"), tasks.map { it.title })
        assertEquals(listOf(at(9), at(11), at(13, 30), at(16)), tasks.map { it.startMillis })
        assertEquals(listOf(at(11), at(14), at(16), at(17)), tasks.map { it.endMillis })
        assertEquals(tasks.size, tasks.map { it.id }.toSet().size)
        assertNull(viewModel.state.value.selectedTaskId)
    }

    @Test
    fun initialWindow_startsAtReferenceMidnightAndSpansThreeDays() {
        val window = viewModel.state.value.window
        assertEquals(at(0), window.startMillis)
        assertEquals(3 * 24 * 60 * 60_000L, window.lengthMillis)
    }

    @Test
    fun selectTask_setsAndClearsSelection() {
        viewModel.selectTask("review")
        assertEquals("review", viewModel.state.value.selectedTaskId)
        viewModel.selectTask(null)
        assertNull(viewModel.state.value.selectedTaskId)
    }

    @Test
    fun commitEdit_replacesOnlyThatTaskAndKeepsRowOrder() {
        val before = viewModel.state.value.tasks
        val edited = before[1].copy(startMillis = at(10), endMillis = at(13))
        viewModel.commitEdit(edited)

        val after = viewModel.state.value.tasks
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(edited, after[1])
        assertEquals(before - before[1], after - after[1])
    }

    @Test
    fun commitEdit_ignoresUnknownTask() {
        val before = viewModel.state.value
        viewModel.commitEdit(before.tasks[0].copy(id = "missing"))
        assertEquals(before, viewModel.state.value)
    }

    @Test
    fun updateConfig_neverChangesTaskTimestamps() {
        val before = viewModel.state.value.tasks
        viewModel.updateConfig(
            TimelineConfig(timeScale = TimeScale.DAYS, unitWidthDp = 720f, snapMinutes = 60, rowHeightDp = 72f),
        )
        assertEquals(TimeScale.DAYS, viewModel.state.value.config.timeScale)
        assertEquals(before, viewModel.state.value.tasks)
    }
}
