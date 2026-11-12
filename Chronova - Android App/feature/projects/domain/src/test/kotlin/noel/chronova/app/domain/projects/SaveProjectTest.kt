package noel.chronova.app.domain.projects

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import noel.chronova.app.domain.ProjectProblem
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.testing.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveProjectTest {
    private val zone = ZoneOffset.UTC
    private val repository = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone))
    private val save = SaveProject(repository, zone)
    private val start = SampleTasks.project.startDate

    @Test
    fun createsANewProject() = runTest {
        val result = save(null, "Launch", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7))
        val id = (result as SaveProjectResult.Created).projectId
        assertEquals("Launch", repository.projects.value.single { it.id == id }.name)
    }

    @Test
    fun refusesInvalidValuesWithoutWriting() = runTest {
        assertEquals(SaveProjectResult.Invalid(ProjectProblem.BlankName), save(null, " ", start, start))
        assertEquals(1, repository.projects.value.size)
    }

    @Test
    fun refusesARangeThatWouldLeaveTasksOutside() = runTest {
        // Every sample task is on the first day.
        val result = save(SampleTasks.PROJECT_ID, "Sample", start.plusDays(1), start.plusDays(2))
        assertEquals(SaveProjectResult.Invalid(ProjectProblem.ExcludesTasks(4)), result)
        assertEquals(SampleTasks.project, repository.projects.value.single())
    }

    @Test
    fun savesAnEditedProject() = runTest {
        val end = SampleTasks.project.endDate.plusDays(3)
        assertEquals(SaveProjectResult.Updated, save(SampleTasks.PROJECT_ID, "Renamed", start, end))
        assertEquals("Renamed", repository.projects.value.single().name)
        assertEquals(end, repository.projects.value.single().endDate)
    }
}
