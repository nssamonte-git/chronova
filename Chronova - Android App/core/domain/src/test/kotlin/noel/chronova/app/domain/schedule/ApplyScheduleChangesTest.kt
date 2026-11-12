package noel.chronova.app.domain.schedule

import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.testing.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyScheduleChangesTest {

    private val zone = ZoneOffset.UTC
    private val repository = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone))
    private val apply = ApplyScheduleChanges(repository, zone)
    private val qa = SampleTasks.tasks(zone)[2]
    private val moved = qa.copy(startMillis = qa.startMillis + 60_000L * 30, endMillis = qa.endMillis + 60_000L * 30)

    @Test
    fun appliesAndReturnsTheUndo() = runTest {
        val result = apply(SampleTasks.PROJECT_ID, listOf(ScheduleChange.UpdateTask(qa, moved)))
        assertEquals(ApplyResult.Applied(listOf(ScheduleChange.UpdateTask(moved, qa))), result)
        assertEquals(moved, repository.tasks.value.first { it.id == qa.id })

        apply(SampleTasks.PROJECT_ID, (result as ApplyResult.Applied).undo)
        assertEquals(qa, repository.tasks.value.first { it.id == qa.id })
    }

    @Test
    fun staleChangesWriteNothing() = runTest {
        repository.updateTask(qa.copy(title = "Edited on another device"))
        val result = apply(SampleTasks.PROJECT_ID, listOf(ScheduleChange.UpdateTask(qa, moved)))
        assertEquals(ApplyResult.Rejected(ChangeProblem.Stale(setOf(qa.id))), result)
        assertEquals("Edited on another device", repository.tasks.value.first { it.id == qa.id }.title)
        assertTrue(repository.applied.isEmpty())
    }

    @Test
    fun oneBadChangeRejectsTheWholeList() = runTest {
        val tasks = SampleTasks.tasks(zone)
        val fine = ScheduleChange.UpdateTask(tasks[0], tasks[0].copy(title = "Fine"))
        val bad = ScheduleChange.UpdateTask(tasks[1], tasks[1].copy(title = " "))
        val result = apply(SampleTasks.PROJECT_ID, listOf(fine, bad))
        assertTrue(result is ApplyResult.Rejected)
        assertEquals(tasks, repository.tasks.value)
    }
}
