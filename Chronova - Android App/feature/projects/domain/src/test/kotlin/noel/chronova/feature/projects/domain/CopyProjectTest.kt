package noel.chronova.feature.projects.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import noel.chronova.core.domain.ProjectProblem
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.testing.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyProjectTest {
    private val zone = ZoneOffset.UTC
    private var ids = 0
    private val tasks = SampleTasks.tasks(zone).mapIndexed { i, task ->
        when (i) {
            0 -> task.copy(progress = 100, notes = "Signed off")
            3 -> task.copy(endMillis = task.startMillis, progress = 100, isMilestone = true)
            else -> task
        }
    }
    private val link = TaskDependency("l1", SampleTasks.PROJECT_ID, "ui-design", "implementation")
    private val repository = FakeProjectRepository(listOf(SampleTasks.project), tasks).also { it.dependencies.value = listOf(link) }
    private val copy = CopyProject(repository, zone, newId = { "c${++ids}" })

    private fun created(result: CopyProjectResult) = (result as CopyProjectResult.Created).projectId

    @Test
    fun theCopyStartsOnTheNewDateWithTheSameLength() = runTest {
        val id = created(copy(SampleTasks.PROJECT_ID, "  Launch v2 ", LocalDate.of(2026, 3, 9)))
        assertEquals(Project(id, "Launch v2", LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 11)), repository.projects.value.single { it.id == id })
        assertEquals(SampleTasks.project, repository.projects.value.first()) // the source is untouched
    }

    @Test
    fun everyTaskMovesByTheSameDaysAndStartsUndone() = runTest {
        val id = created(copy(SampleTasks.PROJECT_ID, "Copy", LocalDate.of(2026, 3, 9)))
        val copied = repository.tasks.value.filter { it.projectId == id }
        val week = 7 * 86_400_000L
        assertEquals(tasks.map { it.startMillis + week }, copied.map { it.startMillis })
        assertEquals(tasks.map { it.endMillis + week }, copied.map { it.endMillis })
        assertEquals(tasks.map { it.title }, copied.map { it.title })
        assertEquals(tasks.map { it.color }, copied.map { it.color })
        assertEquals(tasks.map { it.sortOrder }, copied.map { it.sortOrder })
        assertTrue(copied.all { it.progress == 0 })
        assertEquals("Signed off", copied.first().notes)
        assertTrue(copied.last().isMilestone)
        assertTrue(copied.none { it.id in tasks.map(SchedulerTask::id) })
    }

    @Test
    fun linksPointAtTheCopiedTasks() = runTest {
        val id = created(copy(SampleTasks.PROJECT_ID, "Copy", LocalDate.of(2026, 3, 9)))
        val copied = repository.tasks.value.filter { it.projectId == id }.associateBy { it.title }
        val newLink = repository.dependencies.value.single { it.projectId == id }
        assertEquals(copied.getValue("UI Design").id to copied.getValue("Implementation").id, newLink.predecessorId to newLink.successorId)
    }

    @Test
    fun localTimesSurviveADstChange() = runTest {
        // New York springs forward on Sun 8 Mar 2026: a task at 09:00 on the 2nd is still at 09:00 on the 9th.
        val ny = ZoneId.of("America/New_York")
        val source = Project("p", "Week", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3))
        val nine = LocalDate.of(2026, 3, 2).atTime(9, 0).atZone(ny).toInstant().toEpochMilli()
        val repo = FakeProjectRepository(listOf(source), listOf(SchedulerTask("t", "p", "Stand-up", nine, nine + 900_000)))
        val id = created(CopyProject(repo, ny)("p", "Next week", LocalDate.of(2026, 3, 9)))
        val moved = repo.tasks.value.single { it.projectId == id }
        assertEquals(LocalTime.of(9, 0), java.time.Instant.ofEpochMilli(moved.startMillis).atZone(ny).toLocalTime())
        assertEquals(900_000L, moved.durationMillis)
    }

    @Test
    fun invalidNamesAndGoneSourcesAreRefusedWithoutWriting() = runTest {
        assertEquals(CopyProjectResult.Invalid(ProjectProblem.BlankName), copy(SampleTasks.PROJECT_ID, " ", LocalDate.of(2026, 3, 9)))
        assertEquals(CopyProjectResult.SourceGone, copy("nope", "Copy", LocalDate.of(2026, 3, 9)))
        assertEquals(1, repository.projects.value.size)
    }
}
