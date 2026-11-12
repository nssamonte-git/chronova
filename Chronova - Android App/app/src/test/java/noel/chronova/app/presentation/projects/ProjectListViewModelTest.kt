package noel.chronova.app.presentation.projects

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import noel.chronova.app.domain.ProjectProblem
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.testing.FakeProjectRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectListViewModelTest {

    private val zone = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 11, 11)
    private lateinit var repository: FakeProjectRepository
    private lateinit var viewModel: ProjectListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone))
        viewModel = ProjectListViewModel(repository, zone, today = { today })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startCreate_defaultsToAWeekFromToday() {
        viewModel.startCreate()
        val editor = viewModel.editor.value!!
        assertTrue(editor.isNew)
        assertEquals(today, editor.startDate)
        assertEquals(today.plusDays(6), editor.endDate)
    }

    @Test
    fun create_savesProjectClosesEditorAndOpensIt() = runTest {
        viewModel.startCreate()
        viewModel.onNameChange("  Office move ")
        viewModel.saveEditor()

        val created = repository.projects.value.last()
        assertEquals("Office move", created.name)
        assertNull(viewModel.editor.value)
        assertEquals(created.id, viewModel.openProject.value)
        viewModel.onProjectOpened()
        assertNull(viewModel.openProject.value)
    }

    @Test
    fun blankName_keepsEditorOpenWithProblem() = runTest {
        viewModel.startCreate()
        viewModel.saveEditor()
        assertEquals(ProjectProblem.BlankName, viewModel.editor.value?.problem)
        assertEquals(1, repository.projects.value.size)
        // Typing clears the problem.
        viewModel.onNameChange("A")
        assertNull(viewModel.editor.value?.problem)
    }

    @Test
    fun edit_cannotShrinkPastExistingTasks() = runTest {
        viewModel.startEdit(SampleTasks.project)
        // All sample tasks are on the first day, so dropping that day excludes all four.
        viewModel.onRangeChange(SampleTasks.REFERENCE_DATE.plusDays(1), SampleTasks.REFERENCE_DATE.plusDays(2))
        viewModel.saveEditor()
        assertEquals(ProjectProblem.ExcludesTasks(4), viewModel.editor.value?.problem)
        assertEquals(SampleTasks.project, repository.projects.value.single())
    }

    @Test
    fun edit_renamesAndExtends() = runTest {
        viewModel.startEdit(SampleTasks.project)
        viewModel.onNameChange("Website launch")
        viewModel.onRangeChange(SampleTasks.REFERENCE_DATE, SampleTasks.REFERENCE_DATE.plusDays(13))
        viewModel.saveEditor()
        val saved = repository.projects.value.single()
        assertEquals("Website launch", saved.name)
        assertEquals(SampleTasks.REFERENCE_DATE.plusDays(13), saved.endDate)
        assertNull(viewModel.editor.value)
        assertNull(viewModel.openProject.value)
    }

    @Test
    fun delete_needsConfirmation() = runTest {
        val summary = noel.chronova.app.domain.model.ProjectSummary(SampleTasks.project, 4)
        viewModel.requestDelete(summary)
        assertEquals(1, repository.projects.value.size)
        viewModel.cancelDelete()
        assertEquals(1, repository.projects.value.size)

        viewModel.requestDelete(summary)
        viewModel.confirmDelete()
        assertTrue(repository.projects.value.isEmpty())
        assertTrue(repository.tasks.value.isEmpty())
        assertNull(viewModel.pendingDelete.value)
    }
}
