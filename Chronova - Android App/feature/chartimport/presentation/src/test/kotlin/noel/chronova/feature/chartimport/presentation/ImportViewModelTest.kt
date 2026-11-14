package noel.chronova.feature.chartimport.presentation

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.model.Project
import noel.chronova.core.testing.FakeAuthRepository
import noel.chronova.core.testing.FakeNotificationRepository
import noel.chronova.core.testing.FakeProjectRepository
import noel.chronova.feature.chartimport.domain.ApplyImport
import noel.chronova.feature.chartimport.domain.ChartImages
import noel.chronova.feature.chartimport.domain.ImportApi
import noel.chronova.feature.chartimport.domain.ImportApiException
import noel.chronova.feature.chartimport.domain.ImportUseCases
import noel.chronova.feature.chartimport.domain.ImportedSchedule
import noel.chronova.feature.chartimport.domain.ImportedTask
import noel.chronova.feature.chartimport.domain.LoadImportTarget
import noel.chronova.feature.chartimport.domain.PlanImport
import noel.chronova.feature.chartimport.domain.ReadChart
import noel.chronova.feature.chartimport.domain.UndoImport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val zone: ZoneId = ZoneId.of("Asia/Manila")
private val monday: LocalDate = LocalDate.of(2026, 11, 16)

private fun task(ref: String, title: String, start: Int, end: Int, after: List<String> = emptyList(), milestone: Boolean = false) =
    ImportedTask(ref, title, null, null, start, end, milestone, 0, after)

/** Part of the test chart: three steps over three weeks, the third waiting for the second. */
private val chart = ImportedSchedule(
    ImportedSchedule.Result.OK, ImportedSchedule.Dates.RELATIVE, "Project Timeline", null, null, 21,
    listOf(task("t1", "Initial Client Consult", 0, 6), task("t2", "Project Design Inquiry", 0, 13), task("t3", "Initial Design Concept", 14, 20, listOf("t2"))),
    listOf("No year on the chart."),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val repository = FakeProjectRepository()
    private val notifications = FakeNotificationRepository()
    private var answer: suspend () -> ImportedSchedule = { chart }
    private val cleared = mutableListOf<Unit>()
    private val api = object : ImportApi {
        override suspend fun read(accessToken: String, image: ByteArray, timeZone: String, today: LocalDate, project: Project?) = answer()
    }
    private val images = object : ChartImages {
        override suspend fun newPhotoUri() = "content://imports/photo-1.jpg"
        override suspend fun prepare(uri: String) = ByteArray(10)
        override suspend fun clear() {
            cleared += Unit
        }
    }
    private val useCases = ImportUseCases(
        images,
        ReadChart(api, images, FakeAuthRepository()::accessToken, { zone }, { monday }),
        LoadImportTarget(repository),
        PlanImport(zone),
        ApplyImport(repository, ApplyScheduleChanges(repository, zone), notifications),
        UndoImport(repository, ApplyScheduleChanges(repository, zone)),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(projectId: String? = null) = ImportViewModel(projectId, useCases, { monday }, "Imported schedule")

    private val ImportViewModel.preview get() = (state.value as ImportSheetUi.Preview).preview

    @Test
    fun aPickedImageIsReadIntoAPreviewOfANewProject() {
        val viewModel = viewModel()
        assertEquals(ImportSheetUi.Choose(newProject = true, projectName = ""), viewModel.state.value)

        viewModel.onImageChosen("content://media/picked/7")

        val preview = viewModel.preview
        assertEquals("Project Timeline", preview.projectName)
        assertEquals(monday to monday.plusDays(20), preview.projectStart to preview.projectEnd)
        assertTrue(preview.relative)
        assertEquals(listOf("No year on the chart."), preview.uncertain)
        assertEquals(3, preview.selectedCount)
        assertEquals(1, preview.linkCount)
        assertEquals(1, cleared.size) // camera files cleared after every reading
    }

    @Test
    fun aOneDayTaskShowsTheHoursItWillGet() {
        val tasting = ImportedTask("t4", "Menu tasting", null, null, 10, 10, false, 0, emptyList(), LocalTime.of(10, 0), LocalTime.of(13, 0))
        answer = { chart.copy(tasks = chart.tasks + tasting) }
        val viewModel = viewModel()

        viewModel.onImageChosen("content://media/picked/7")

        val rows = viewModel.preview.rows
        assertEquals(LocalTime.of(10, 0) to LocalTime.of(13, 0), rows.last().startTime to rows.last().endTime)
        assertEquals(null to null, rows.first().startTime to rows.first().endTime) // whole days
    }

    @Test
    fun aPhotoIsReadOnlyWhenTheCameraSavedIt() {
        val viewModel = viewModel()
        viewModel.takePhoto()
        assertEquals("content://imports/photo-1.jpg", viewModel.camera.value)
        viewModel.onCameraOpened(true)
        assertNull(viewModel.camera.value)

        viewModel.onPhotoTaken(false) // backed out of the camera
        assertTrue(viewModel.state.value is ImportSheetUi.Choose)

        viewModel.takePhoto()
        viewModel.onCameraOpened(true)
        viewModel.onPhotoTaken(true)
        assertTrue(viewModel.state.value is ImportSheetUi.Preview)

        viewModel.takePhoto()
        viewModel.onCameraOpened(false)
        assertEquals(ImportSheetUi.Problem(ImportProblemUi.NoCamera), viewModel.state.value)
    }

    @Test
    fun movingTheStartMovesANewProjectAndApplyCreatesItWithUndo() {
        val viewModel = viewModel()
        viewModel.onImageChosen("content://picked")
        viewModel.setStart(monday.plusDays(7))
        assertEquals(monday.plusDays(7) to monday.plusDays(27), viewModel.preview.projectStart to viewModel.preview.projectEnd)
        viewModel.toggle("t1")
        assertEquals(2, viewModel.preview.selectedCount)

        viewModel.apply()

        val done = (viewModel.state.value as ImportSheetUi.Done).done
        assertEquals(ImportDoneUi(done.projectId, "Project Timeline", 2, newProject = true), done)
        assertEquals(listOf("Project Design Inquiry", "Initial Design Concept"), repository.tasks.value.map { it.title })
        assertEquals(1, repository.dependencies.value.size)

        viewModel.undo()
        assertEquals(true, (viewModel.state.value as ImportSheetUi.Done).done.undone)
        assertTrue(repository.projects.value.isEmpty())
    }

    @Test
    fun applyExplainsWhatIsWrongWithoutWriting() {
        val viewModel = viewModel()
        viewModel.onImageChosen("content://picked")
        viewModel.setName(" ")
        viewModel.apply()
        assertEquals(ImportPlanProblemUi.BlankName, viewModel.preview.problem)

        viewModel.setName("Kitchen")
        listOf("t1", "t2", "t3").forEach(viewModel::toggle)
        viewModel.apply()
        assertEquals(ImportPlanProblemUi.NothingSelected, viewModel.preview.problem)
        assertTrue(repository.projects.value.isEmpty())
    }

    @Test
    fun addingToAProjectUntickesWhatDoesNotFit() {
        val project = Project("p1", "Office move", monday, monday.plusDays(9)) // 10 days
        repository.projects.value = listOf(project)
        val viewModel = viewModel("p1")
        assertEquals(ImportSheetUi.Choose(newProject = false, projectName = "Office move"), viewModel.state.value)

        viewModel.onImageChosen("content://picked")
        val rows = viewModel.preview.rows
        assertEquals(listOf(true, false, false), rows.map { it.fits })
        assertEquals(listOf(true, false, false), rows.map { it.selected })

        viewModel.apply()
        assertEquals(1, (viewModel.state.value as ImportSheetUi.Done).done.taskCount)
        assertEquals(1, notifications.added.size)
    }

    @Test
    fun whatTheServerSaysBecomesAProblemToTryAgainFrom() {
        val viewModel = viewModel()
        val cases = listOf(
            suspend { chart.copy(result = ImportedSchedule.Result.NOT_A_SCHEDULE, tasks = emptyList()) } to ImportProblemUi.NotASchedule,
            suspend { chart.copy(result = ImportedSchedule.Result.UNREADABLE, tasks = emptyList()) } to ImportProblemUi.Unreadable,
            suspend { throw ImportApiException(ImportApiException.Kind.NETWORK) } to ImportProblemUi.Offline,
            suspend { throw ImportApiException(ImportApiException.Kind.RATE_LIMITED) } to ImportProblemUi.TooMany,
            suspend { throw ImportApiException(ImportApiException.Kind.DISABLED) } to ImportProblemUi.SwitchedOff,
            suspend { throw ImportApiException(ImportApiException.Kind.BAD_IMAGE) } to ImportProblemUi.BadImage,
            suspend { throw IllegalStateException("bug") } to ImportProblemUi.Unavailable,
        )
        for ((reply, problem) in cases) {
            answer = reply
            viewModel.onImageChosen("content://picked")
            assertEquals(ImportSheetUi.Problem(problem), viewModel.state.value)
            viewModel.backToChoose()
        }
    }

    @Test
    fun readingCanBeCancelled() {
        val waiting = CompletableDeferred<ImportedSchedule>()
        answer = { waiting.await() }
        val viewModel = viewModel()
        viewModel.onImageChosen("content://picked")
        assertEquals(ImportSheetUi.Reading, viewModel.state.value)

        viewModel.cancelReading()
        waiting.complete(chart)
        assertTrue(viewModel.state.value is ImportSheetUi.Choose)
    }

    @Test
    fun aProjectThatIsGoneSaysSo() {
        assertEquals(ImportSheetUi.Problem(ImportProblemUi.ProjectGone), viewModel("gone").state.value)
    }
}
