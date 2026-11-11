package noel.chronova.app.presentation.timeline

import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.model.TimeScale
import noel.chronova.app.domain.model.TimelineConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectTimelineViewModelTest {

    private val zone = ZoneOffset.UTC
    private lateinit var repository: FakeProjectRepository
    private lateinit var viewModel: ProjectTimelineViewModel

    private fun at(hour: Int, minute: Int = 0): Long =
        SampleTasks.REFERENCE_DATE.atTime(hour, minute).toInstant(zone).toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone))
        viewModel = ProjectTimelineViewModel(SampleTasks.PROJECT_ID, repository, zone)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Collects the state for the duration of the test (it is shared WhileSubscribed). */
    private fun TestScope.timeline(): () -> TimelineUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return { (viewModel.state.value as TimelineScreenState.Ready).timeline }
    }

    @Test
    fun loadsProjectTasksAtSpecifiedTimes() = runTest {
        val state = timeline()
        val tasks = state().tasks
        assertEquals(SampleTasks.project.name, state().projectName)
        assertEquals(listOf("UI Design", "Implementation", "QA Testing", "Review"), tasks.map { it.title })
        assertEquals(listOf(at(9), at(11), at(13, 30), at(16)), tasks.map { it.startMillis })
        assertEquals(listOf(at(11), at(14), at(16), at(17)), tasks.map { it.endMillis })
        assertNull(state().selectedTaskId)
    }

    @Test
    fun windowCoversTheProjectDays() = runTest {
        val window = timeline()().window
        assertEquals(at(0), window.startMillis)
        assertEquals(3 * 24 * 60 * 60_000L, window.lengthMillis)
    }

    @Test
    fun selectTask_setsAndClearsSelection() = runTest {
        val state = timeline()
        viewModel.selectTask("review")
        assertEquals("review", state().selectedTaskId)
        viewModel.selectTask(null)
        assertNull(state().selectedTaskId)
    }

    @Test
    fun selectionOfARemovedTaskIsDropped() = runTest {
        val state = timeline()
        viewModel.selectTask("review")
        repository.tasks.value = repository.tasks.value.filterNot { it.id == "review" }
        assertNull(state().selectedTaskId)
    }

    @Test
    fun commitEdit_savesThroughRepositoryAndKeepsRowOrder() = runTest {
        val state = timeline()
        val before = state().tasks
        val edited = before[1].copy(startMillis = at(10), endMillis = at(13))
        viewModel.commitEdit(edited)

        assertEquals(listOf(edited), repository.updates)
        val after = state().tasks
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(edited, after[1])
    }

    @Test
    fun commitEdit_ignoresUnknownTaskOrOtherProject() = runTest {
        val state = timeline()
        val task = state().tasks[0]
        viewModel.commitEdit(task.copy(id = "missing"))
        viewModel.commitEdit(task.copy(projectId = "other"))
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun deletedProjectReportsNotFound() = runTest {
        timeline()
        repository.projects.value = emptyList()
        assertEquals(TimelineScreenState.NotFound, viewModel.state.value)
    }

    @Test
    fun updateConfig_neverChangesTaskTimestamps() = runTest {
        val state = timeline()
        val before = state().tasks
        viewModel.setTimeScale(TimeScale.DAYS)
        viewModel.zoomIn()
        viewModel.setSnapMinutes(60)
        viewModel.changeRowHeight(+2)
        viewModel.setTimeScale(TimeScale.HOURS)
        viewModel.zoomOut()
        viewModel.resetZoom()

        assertEquals(TimelineConfig(snapMinutes = 60, rowHeightDp = 72f), state().config)
        assertEquals(before, state().tasks)
        assertTrue(repository.updates.isEmpty())
    }
}
