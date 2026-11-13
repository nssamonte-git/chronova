package noel.chronova.feature.export.presentation

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import noel.chronova.core.model.Project
import noel.chronova.core.testing.FakeProjectRepository
import noel.chronova.feature.export.domain.ExportFormat
import noel.chronova.feature.export.domain.ExportProject
import noel.chronova.feature.export.domain.ExportedFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {
    private val saved = mutableListOf<Triple<String, String, Int>>()
    private var failWrites = false
    private val repository = FakeProjectRepository(listOf(Project("p1", "Office move", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6))))
    private lateinit var viewModel: ExportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val export = ExportProject(
            repository,
            pdf = { _, _, _ -> ByteArray(10) },
            files = { name, mime, bytes ->
                if (failWrites) error("disk full")
                saved += Triple(name, mime, bytes.size)
                ExportedFile(name, mime, "content://exports/$name")
            },
            zone = ZoneOffset.UTC,
        )
        viewModel = ExportViewModel("p1", "Office move", export)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsOnPdfWithNotesOnAndExportsTheChosenFormat() {
        assertEquals(ExportSheetState("Office move"), viewModel.state.value)
        viewModel.chooseFormat(ExportFormat.CALENDAR)
        viewModel.setIncludeNotes(false)
        viewModel.export()
        assertEquals("Office move.ics", saved.single().first)
        assertEquals(ShareFileUi("Office move.ics", "text/calendar", "content://exports/Office move.ics"), viewModel.share.value)
        assertFalse(viewModel.state.value.working)

        viewModel.onShared()
        assertNull(viewModel.share.value) // shared once
    }

    @Test
    fun aDeletedProjectOrAFailedWriteIsSaid() {
        repository.projects.value = emptyList()
        viewModel.export()
        assertEquals(ExportProblemUi.ProjectGone, viewModel.state.value.problem)

        repository.projects.value = listOf(Project("p1", "Office move", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6)))
        failWrites = true
        viewModel.export()
        assertEquals(ExportProblemUi.WriteFailed, viewModel.state.value.problem)
        assertNull(viewModel.share.value)
    }
}
