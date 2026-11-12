package noel.chronova.feature.timeline.domain

import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.testing.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineUseCasesTest {
    private val zone = ZoneOffset.UTC
    private val project = SampleTasks.PROJECT_ID
    private val repository = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone))
    private val task = { id: String -> repository.tasks.value.first { it.id == id } }

    private fun link(from: String, to: String) = TaskDependency("dep-$from-$to", project, from, to)

    @Test
    fun saveTaskKeepsOnlyLivePredecessorsThatCloseNoCycle() = runTest {
        // review already precedes implementation, so implementation preceding review would close a cycle.
        repository.dependencies.value = listOf(link("review", "implementation"))
        val review = task("review")
        SaveTask(repository)(
            projectId = project,
            taskId = review.id,
            existing = review,
            title = "Final review",
            startMillis = review.startMillis,
            endMillis = review.endMillis,
            color = TaskColor.PURPLE,
            predecessorIds = setOf("ui-design", "implementation", "gone"),
        )
        assertEquals("Final review", task("review").title)
        val predecessors = repository.dependencies.value.filter { it.successorId == "review" }.map { it.predecessorId }.toSet()
        // "implementation" would close a cycle, "gone" doesn't exist.
        assertEquals(setOf("ui-design"), predecessors)
    }

    @Test
    fun saveTaskCreatesANewTask() = runTest {
        val start = task("ui-design").startMillis
        val id = SaveTask(repository)(project, null, null, "Docs", start, start + 3_600_000, TaskColor.GREEN, emptySet())
        assertEquals("Docs", task(id).title)
    }

    @Test
    fun deleteTaskRemovesItsLinksAndCanBeUndone() = runTest {
        repository.dependencies.value = listOf(link("ui-design", "review"), link("implementation", "qa-testing"))
        val apply = ApplyScheduleChanges(repository, zone)
        val result = DeleteTask(apply)(project, task("review"), repository.dependencies.value)
        assertTrue(result is ApplyResult.Applied)
        assertTrue(repository.tasks.value.none { it.id == "review" })
        assertEquals(listOf("implementation"), repository.dependencies.value.map { it.predecessorId })

        UndoScheduleChanges(apply)(project, (result as ApplyResult.Applied).undo)
        assertTrue(repository.tasks.value.any { it.id == "review" })
        assertEquals(2, repository.dependencies.value.size)
    }
}
