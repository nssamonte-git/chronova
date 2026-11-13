package noel.chronova.feature.projects.presentation

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import noel.chronova.core.domain.SampleTasks
import noel.chronova.feature.projects.domain.ProjectUseCases
import noel.chronova.core.testing.FakeProjectRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import noel.chronova.feature.projects.domain.SaveProject
import noel.chronova.feature.projects.domain.DeleteProject
import noel.chronova.core.domain.CopyProject
import noel.chronova.core.domain.repository.ProjectRepository
import java.time.ZoneId

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
        viewModel = ProjectListViewModel(repository, projectUseCases(repository, zone), today = { today }, zone = { zone })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun eachProjectsRowCarriesItsOwnTasksAsBars() = runTest {
        val other = noel.chronova.core.model.Project("other", "Other", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2))
        repository.projects.value = repository.projects.value + other
        val rows = listed().associateBy { it.id }
        val sample = SampleTasks.tasks(zone)
        assertEquals(sample.map { it.color }, rows.getValue(SampleTasks.PROJECT_ID).lanes.map { it.single().color })
        assertEquals(emptyList<List<ProjectBarUi>>(), rows.getValue("other").lanes)
    }

    @Test
    fun aNewTaskAppearsOnItsProjectsCard() = runTest {
        assertEquals(4, listed().single().lanes.size)
        repository.tasks.value = repository.tasks.value + SampleTasks.tasks(zone).first().copy(id = "added", sortOrder = 99)
        assertEquals(5, (viewModel.state.value as ProjectListState.Loaded).projects.single().lanes.size)
    }

    @Test
    fun startCreate_defaultsToAWeekFromToday() {
        viewModel.startCreate()
        val editor = viewModel.editor.value!!
        assertTrue(editor.isNew)
        assertEquals(today, editor.startDate)
        assertEquals(today.plusDays(6), editor.endDate)
    }

    /** The list, collected so the sheet can offer its projects to start from. */
    private fun kotlinx.coroutines.test.TestScope.listed(): List<ProjectRowUi> {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return (viewModel.state.value as ProjectListState.Loaded).projects
    }

    @Test
    fun duplicate_copiesToTheChosenStartAndOpensTheCopy() = runTest {
        val sample = listed().single()
        viewModel.startDuplicate(sample, "Copy of Sample project")
        val editor = viewModel.editor.value!!
        assertTrue(editor.isCopy)
        assertEquals(today, editor.startDate)
        assertEquals(today.plusDays(2), editor.endDate) // the sample runs three days

        viewModel.onRangeChange(today.plusDays(7), today.plusDays(30)) // a copy keeps its length
        assertEquals(today.plusDays(9), viewModel.editor.value!!.endDate)
        viewModel.saveEditor()

        val copy = repository.projects.value.single { it.name == "Copy of Sample project" }
        assertEquals(today.plusDays(7) to today.plusDays(9), copy.startDate to copy.endDate)
        assertEquals(4, repository.tasks.value.count { it.projectId == copy.id })
        assertEquals(copy.id, viewModel.openProject.value)
        assertNull(viewModel.editor.value)
    }

    @Test
    fun startFrom_switchesANewProjectToACopyAndBack() = runTest {
        listed()
        viewModel.startCreate()
        assertEquals(listOf("Sample project"), viewModel.editor.value!!.sources.map { it.name })

        viewModel.onStartFrom(SampleTasks.PROJECT_ID)
        assertEquals(today.plusDays(2), viewModel.editor.value!!.endDate)
        assertEquals("Sample project", viewModel.editor.value!!.copyFrom?.name)

        viewModel.onStartFrom(null)
        assertNull(viewModel.editor.value!!.copyFrom)
    }

    @Test
    fun copyingAProjectThatWasDeletedMeanwhileSaysSo() = runTest {
        val sample = listed().single()
        viewModel.startDuplicate(sample, "Copy")
        repository.projects.value = emptyList()
        viewModel.saveEditor()
        assertEquals(ProjectProblemUi.SourceGone, viewModel.editor.value?.problem)
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
        assertEquals(ProjectProblemUi.BlankName, viewModel.editor.value?.problem)
        assertEquals(1, repository.projects.value.size)
        // Typing clears the problem.
        viewModel.onNameChange("A")
        assertNull(viewModel.editor.value?.problem)
    }

    @Test
    fun aProjectThatEndsBeforeItStartsIsRefused() = runTest {
        viewModel.startCreate()
        viewModel.onNameChange("Kitchen")
        viewModel.onRangeChange(SampleTasks.REFERENCE_DATE.plusDays(5), SampleTasks.REFERENCE_DATE)
        viewModel.saveEditor()
        assertEquals(ProjectProblemUi.EndBeforeStart, viewModel.editor.value?.problem)
        assertEquals(1, repository.projects.value.size)
    }

    @Test
    fun edit_cannotShrinkPastExistingTasks() = runTest {
        viewModel.startEdit(noel.chronova.core.model.ProjectSummary(SampleTasks.project, 4).toUi())
        // All sample tasks are on the first day, so dropping that day excludes all four.
        viewModel.onRangeChange(SampleTasks.REFERENCE_DATE.plusDays(1), SampleTasks.REFERENCE_DATE.plusDays(2))
        viewModel.saveEditor()
        assertEquals(ProjectProblemUi.ExcludesTasks(4), viewModel.editor.value?.problem)
        assertEquals(SampleTasks.project, repository.projects.value.single())
    }

    @Test
    fun edit_renamesAndExtends() = runTest {
        viewModel.startEdit(noel.chronova.core.model.ProjectSummary(SampleTasks.project, 4).toUi())
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
        val summary = noel.chronova.core.model.ProjectSummary(SampleTasks.project, 4)
        viewModel.requestDelete(summary.toUi())
        assertEquals(1, repository.projects.value.size)
        viewModel.cancelDelete()
        assertEquals(1, repository.projects.value.size)

        viewModel.requestDelete(summary.toUi())
        viewModel.confirmDelete()
        assertTrue(repository.projects.value.isEmpty())
        assertTrue(repository.tasks.value.isEmpty())
        assertNull(viewModel.pendingDelete.value)
    }
}

/** The use cases the app wires, over a fake repository. */
private fun projectUseCases(repository: ProjectRepository, zone: ZoneId) = ProjectUseCases(
    save = SaveProject(repository, zone),
    delete = DeleteProject(repository),
    copy = CopyProject(repository, zone),
)
