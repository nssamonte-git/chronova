package noel.chronova.feature.chartimport.presentation

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneId
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.model.Project
import noel.chronova.core.testing.FakeAuthRepository
import noel.chronova.core.testing.FakeNotificationRepository
import noel.chronova.core.testing.FakeProjectRepository
import noel.chronova.feature.chartimport.domain.ApplyImport
import noel.chronova.feature.chartimport.domain.ChartImages
import noel.chronova.feature.chartimport.domain.ImportApi
import noel.chronova.feature.chartimport.domain.ImportUseCases
import noel.chronova.feature.chartimport.domain.ImportedSchedule
import noel.chronova.feature.chartimport.domain.ImportedTask
import noel.chronova.feature.chartimport.domain.LoadImportTarget
import noel.chronova.feature.chartimport.domain.PlanImport
import noel.chronova.feature.chartimport.domain.ReadChart
import noel.chronova.feature.chartimport.domain.UndoImport
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The import sheet on a device (this module's own test app), from the real ViewModel over fakes. */
@RunWith(AndroidJUnit4::class)
class ImportSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val zone = ZoneId.of("Asia/Manila")
    private val repository = FakeProjectRepository()
    private val chart = ImportedSchedule(
        ImportedSchedule.Result.OK, ImportedSchedule.Dates.RELATIVE, "Project Timeline", null, null, 14,
        listOf(
            ImportedTask("t1", "Initial Client Consult", null, null, 0, 6, false, 0, emptyList()),
            ImportedTask("t2", "Project Design Inquiry", null, null, 0, 13, false, 0, emptyList()),
        ),
        emptyList(),
    )
    private val images = object : ChartImages {
        override suspend fun newPhotoUri() = "content://imports/photo.jpg"
        override suspend fun prepare(uri: String) = ByteArray(1)
        override suspend fun clear() = Unit
    }
    private val api = object : ImportApi {
        override suspend fun read(accessToken: String, image: ByteArray, timeZone: String, today: LocalDate, project: Project?) = chart
    }
    private val viewModel = ImportViewModel(
        null,
        ImportUseCases(
            images,
            ReadChart(api, images, FakeAuthRepository()::accessToken, { zone }),
            LoadImportTarget(repository),
            PlanImport(zone),
            ApplyImport(repository, ApplyScheduleChanges(repository, zone), FakeNotificationRepository()),
            UndoImport(repository, ApplyScheduleChanges(repository, zone)),
        ),
        { LocalDate.of(2026, 11, 16) },
        "Imported schedule",
    )
    private val opened = mutableListOf<String>()

    private fun text(id: Int) = res.getString(id)
    private fun plural(id: Int, n: Int) = res.getQuantityString(id, n, n)

    @Test
    fun previewUntickApplyAndUndo() {
        compose.setContent {
            ChronovaTheme { ImportScreen(viewModel, onDismiss = {}, onOpenProject = { opened += it }) }
        }
        compose.onNodeWithText(text(R.string.import_take_photo)).assertIsEnabled()
        compose.onNodeWithText(text(R.string.import_choose_image)).assertIsEnabled()

        compose.runOnIdle { viewModel.onImageChosen("content://media/picked/1") } // the picker's answer
        compose.onNodeWithText(plural(R.plurals.import_found_tasks, 2)).assertExists()
        compose.onNodeWithText("Initial Client Consult").assertExists()
        compose.onNodeWithText(plural(R.plurals.import_apply_new, 2)).assertIsEnabled()

        compose.onNodeWithText("Initial Client Consult").performClick() // unticks the row
        compose.onNodeWithText(plural(R.plurals.import_apply_new, 1)).assertIsEnabled()
        compose.onNodeWithText("Project Design Inquiry").performClick()
        compose.onNodeWithText(plural(R.plurals.import_apply_new, 0)).assertIsNotEnabled()
        compose.onNodeWithText("Project Design Inquiry").performClick()

        compose.onNodeWithText(plural(R.plurals.import_apply_new, 1)).performClick()
        compose.onNodeWithText(res.getQuantityString(R.plurals.import_done_body, 1, 1, "Project Timeline")).assertExists()
        assertEquals(listOf("Project Design Inquiry"), repository.tasks.value.map { it.title })

        compose.onNodeWithText(text(R.string.import_undo)).performClick()
        compose.onNodeWithText(text(R.string.import_undone)).assertExists()
        assertEquals(emptyList<Project>(), repository.projects.value)
        compose.onNodeWithText(text(R.string.import_close)).performClick()
        assertEquals(emptyList<String>(), opened)
    }
}
