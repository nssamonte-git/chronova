package noel.chronova.feature.timeline.domain

import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.testing.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushDependentsTest {
    private val zone = ZoneOffset.UTC
    private val project = SampleTasks.PROJECT_ID
    private val hour = 3_600_000L
    // UI Design 9–11 → Implementation 11–14 → Review 16–17 (QA Testing 13:30–16 is unlinked).
    private val repository = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone)).also {
        it.dependencies.value = listOf(link("ui-design", "implementation"), link("implementation", "review"))
    }
    private val push = PushDependents(repository, ApplyScheduleChanges(repository, zone), zone)
    private fun task(id: String) = repository.tasks.value.first { it.id == id }
    private fun link(from: String, to: String) = TaskDependency("dep-$from-$to", project, from, to)

    private fun move(id: String, byHours: Long) {
        repository.tasks.value = repository.tasks.value.map {
            if (it.id == id) it.copy(startMillis = it.startMillis + byHours * hour, endMillis = it.endMillis + byHours * hour) else it
        }
    }

    @Test
    fun nothingToPushWhileEverythingStillStartsAfter() = runTest {
        assertEquals(DependentsPlan.NothingToPush, push.plan(project, "ui-design"))
        move("ui-design", -1) // earlier never pulls anything along
        assertEquals(DependentsPlan.NothingToPush, push.plan(project, "ui-design"))
    }

    @Test
    fun aLaterTaskOffersAndPushesTheChainJustEnoughThenUndoes() = runTest {
        val implementation = task("implementation")
        move("ui-design", 2) // now 11–13, overlapping Implementation 11–14
        assertEquals(DependentsPlan.CanPush("UI Design", 1), push.plan(project, "ui-design"))

        val result = push.push(project, "ui-design") as PushResult.Pushed
        assertEquals(1, result.count)
        assertEquals(implementation.startMillis + 2 * hour, task("implementation").startMillis) // starts when UI Design ends
        assertEquals(implementation.durationMillis, task("implementation").durationMillis)
        // Review (16–17) still starts after Implementation's new end (16:00), so it stays.
        assertEquals(SampleTasks.tasks(zone).first { it.id == "review" }, task("review"))

        ApplyScheduleChanges(repository, zone)(project, result.undo)
        assertEquals(implementation, task("implementation"))
    }

    @Test
    fun pushingPastTheProjectsEndIsRefusedAndMovesNothing() = runTest {
        move("ui-design", 60) // two and a half days later: Implementation would end after the project
        val plan = push.plan(project, "ui-design") as DependentsPlan.DoesNotFit
        assertTrue("Implementation" in plan.titles)
        val before = repository.tasks.value
        assertTrue(push.push(project, "ui-design") is PushResult.DoesNotFit)
        assertEquals(before, repository.tasks.value)
    }
}
