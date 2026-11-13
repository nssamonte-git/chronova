package noel.chronova.feature.projects.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneOffset
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.testing.FakeProjectRepository
import noel.chronova.core.ui.TimeFormat
import noel.chronova.core.ui.sync.SyncUi
import noel.chronova.feature.projects.domain.CopyProject
import noel.chronova.feature.projects.domain.DeleteProject
import noel.chronova.feature.projects.domain.ProjectUseCases
import noel.chronova.feature.projects.domain.SaveProject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import noel.chronova.core.ui.R as CoreUiR

/**
 * The project list, rendered on a device (this module's own test app) from the real
 * [ProjectListViewModel] over an in-memory repository. Only visible text is checked.
 */
@RunWith(AndroidJUnit4::class)
class ProjectListScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 11)

    private val office = Project("office", "Office move", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 20))
    private val launch = Project("launch", "Launch", LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 8))
    private val empty = Project("empty", "Garden", LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14))

    private fun task(id: String, projectId: String, order: Int): SchedulerTask {
        val start = LocalDate.of(2026, 11, 3).atTime(9, 0).toInstant(zone).toEpochMilli()
        return SchedulerTask(id, projectId, "Task $id", start, start + 3_600_000L, TaskColor.BLUE, order)
    }

    private lateinit var repository: FakeProjectRepository
    private lateinit var viewModel: ProjectListViewModel
    private val opened = mutableListOf<String>()
    private val exported = mutableListOf<String>()

    private fun show(projects: List<Project>, tasks: List<SchedulerTask> = emptyList()) {
        repository = FakeProjectRepository(projects, tasks)
        viewModel = ProjectListViewModel(
            repository,
            ProjectUseCases(save = SaveProject(repository, zone), delete = DeleteProject(repository), copy = CopyProject(repository, zone)),
            today = { today },
        )
        compose.setContent {
            ChronovaTheme {
                ProjectListScreen(
                    viewModel = viewModel,
                    onOpenProject = { opened += it },
                    onOpenAccount = {},
                    syncUi = SyncUi(),
                    onSync = {},
                    unreadNotifications = 0,
                    onOpenNotifications = {},
                    onExport = { id, _ -> exported += id },
                )
            }
        }
    }

    /**
     * The "New project" FAB. Its label is under clearAndSetSemantics inside Material's extended FAB, so
     * the button node itself has no text; the label is found in the unmerged tree and tapped there.
     */
    private fun newProjectButton() = compose.onNodeWithText(res.getString(R.string.project_new), useUnmergedTree = true)

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())
    private fun nameField() = compose.onNode(hasSetTextAction() and hasText(res.getString(R.string.project_name)))

    @Test
    fun theNewProjectButtonHasALabelForTalkBack() {
        show(emptyList())
        // What an accessibility service sees: the merged button node, named and clickable.
        compose.onNode(hasContentDescription(res.getString(R.string.project_new)) and hasClickAction()).assertExists().performClick()
        compose.onNode(hasSetTextAction() and hasText(res.getString(R.string.project_name))).assertExists()
    }

    @Test
    fun listsEachProjectWithItsDatesAndTaskCount() {
        show(
            listOf(office, launch, empty),
            listOf(task("a", "office", 0), task("b", "office", 1), task("c", "office", 2), task("d", "launch", 0)),
        )
        compose.onNodeWithText("Office move").assertIsDisplayed()
        compose.onNodeWithText(TimeFormat.dateRange(office.startDate, office.endDate)).assertIsDisplayed()
        compose.onNodeWithText(res.getQuantityString(R.plurals.task_count, 3, 3)).assertIsDisplayed()

        // Across a year end, both years are shown.
        compose.onNodeWithText("Launch").assertIsDisplayed()
        compose.onNodeWithText(TimeFormat.dateRange(launch.startDate, launch.endDate)).assertIsDisplayed()
        compose.onNodeWithText(res.getQuantityString(R.plurals.task_count, 1, 1)).assertIsDisplayed()

        // A one-day project shows a single date.
        compose.onNodeWithText("Garden").assertIsDisplayed()
        compose.onNodeWithText(TimeFormat.dateRange(empty.startDate, empty.endDate)).assertIsDisplayed()
        compose.onNodeWithText(res.getQuantityString(R.plurals.task_count, 0, 0)).assertIsDisplayed()

        compose.onNodeWithText(res.getString(R.string.projects_empty)).assertDoesNotExist()
    }

    @Test
    fun saysHowToStartWhenThereAreNoProjects() {
        show(emptyList())
        compose.onNodeWithText(res.getString(R.string.projects_empty)).assertIsDisplayed()
        newProjectButton().assertIsDisplayed()
    }

    @Test
    fun duplicatingAProjectCopiesItsTasksAndOpensTheCopy() {
        show(listOf(office), listOf(task("a", "office", 0), task("b", "office", 1)))
        compose.onNodeWithContentDescription(res.getString(R.string.project_actions, "Office move")).performClick()
        compose.onNodeWithText(res.getString(R.string.duplicate)).performClick()
        compose.onNodeWithText(res.getString(R.string.project_copy_title, "Office move")).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Copy of Office move")).assertIsDisplayed()

        button(res.getString(R.string.create)).performClick()
        compose.waitForIdle()
        val copy = repository.projects.value.single { it.name == "Copy of Office move" }
        assertEquals(today, copy.startDate) // the copy starts today and keeps the 19-day length
        assertEquals(today.plusDays(18), copy.endDate)
        assertEquals(2, repository.tasks.value.count { it.projectId == copy.id })
        assertEquals(listOf(copy.id), opened)
    }

    @Test
    fun exportInAProjectsMenuAsksTheShellToExportIt() {
        show(listOf(office, launch))
        compose.onNodeWithContentDescription(res.getString(R.string.project_actions, "Launch")).performClick()
        compose.onNodeWithText(res.getString(R.string.export)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("launch"), exported)
    }

    @Test
    fun tappingAProjectOpensIt() {
        show(listOf(office, launch))
        compose.onNodeWithText("Launch").performClick()
        compose.waitForIdle()
        assertEquals(listOf("launch"), opened)
    }

    @Test
    fun creatingWithoutANameAsksForOneAndKeepsTheSheetOpen() {
        show(listOf(office))
        newProjectButton().performClick()
        // The sheet proposes a week from today.
        compose.onNodeWithText(TimeFormat.dateRange(today, today.plusDays(6)), substring = true).assertIsDisplayed()
        button(res.getString(R.string.create)).performClick()

        compose.onNodeWithText(res.getString(R.string.problem_blank_name)).assertIsDisplayed()
        nameField().assertIsDisplayed()
        assertEquals(1, repository.projects.value.size)
        assertEquals(emptyList<String>(), opened)

        // Typing a name clears the message.
        nameField().performTextInput("Kitchen")
        compose.onNodeWithText(res.getString(R.string.problem_blank_name)).assertDoesNotExist()
    }

    @Test
    fun creatingAProjectSavesItAndOpensIt() {
        show(listOf(office))
        newProjectButton().performClick()
        nameField().performTextInput("Kitchen")
        button(res.getString(R.string.create)).performClick()

        compose.waitUntil(5_000) { opened.isNotEmpty() }
        val created = repository.projects.value.single { it.name == "Kitchen" }
        assertEquals(listOf(created.id), opened)
        assertEquals(today, created.startDate)
        assertEquals(today.plusDays(6), created.endDate)
        nameField().assertDoesNotExist()
        compose.onNodeWithText("Kitchen").assertIsDisplayed()
    }

    @Test
    fun creatingWithTheEndBeforeTheStartIsRefused() {
        show(listOf(office))
        newProjectButton().performClick()
        nameField().performTextInput("Kitchen")
        // The date picker reports its range through this intent; it can't pick an end before the
        // start itself, so the range is given here directly.
        compose.runOnIdle { viewModel.onRangeChange(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 10)) }
        button(res.getString(R.string.create)).performClick()

        compose.onNodeWithText(res.getString(R.string.problem_end_before_start)).assertIsDisplayed()
        nameField().assertIsDisplayed()
        assertEquals(listOf(office), repository.projects.value)
        assertEquals(emptyList<String>(), opened)
    }
}
