package noel.chronova.core.domain.schedule

import java.time.ZoneOffset
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.domain.TaskProblem
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleChangesTest {

    private val zone = ZoneOffset.UTC
    private val tasks = SampleTasks.tasks(zone)
    private val design = tasks[0]
    private val build = tasks[1]
    private val qa = tasks[2]
    private val link = TaskDependency("l1", SampleTasks.PROJECT_ID, design.id, build.id)
    private val state = ScheduleState(SampleTasks.project, tasks, listOf(link))

    private val hour = 60 * 60_000L

    private fun shifted(task: SchedulerTask, millis: Long) =
        task.copy(startMillis = task.startMillis + millis, endMillis = task.endMillis + millis)

    @Test
    fun update_appliesWhenTheTaskIsAsExpected() {
        val moved = shifted(build, hour)
        val changes = listOf(ScheduleChange.UpdateTask(build, moved))
        assertNull(ScheduleChanges.problemWith(state, changes, zone))
        assertEquals(moved, ScheduleChanges.apply(state, changes).state.tasks.first { it.id == build.id })
    }

    @Test
    fun update_isStaleWhenTheTaskChangedSince() {
        val editedMeanwhile = state.copy(tasks = tasks.map { if (it.id == build.id) it.copy(title = "Renamed") else it })
        val changes = listOf(ScheduleChange.UpdateTask(build, shifted(build, hour)))
        assertEquals(ChangeProblem.Stale(setOf(build.id)), ScheduleChanges.problemWith(editedMeanwhile, changes, zone))
    }

    @Test
    fun laterChangesSeeEarlierOnesInTheSameList() {
        val once = shifted(qa, hour)
        val twice = shifted(once, hour)
        val changes = listOf(ScheduleChange.UpdateTask(qa, once), ScheduleChange.UpdateTask(once, twice))
        assertNull(ScheduleChanges.problemWith(state, changes, zone))
    }

    @Test
    fun missingProjectIsReported() {
        assertEquals(ChangeProblem.ProjectMissing, ScheduleChanges.problemWith(null, emptyList(), zone))
    }

    @Test
    fun taskRulesAreChecked() {
        val outside = build.copy(endMillis = SampleTasks.project.window(zone).endMillis + hour)
        assertEquals(
            ChangeProblem.InvalidTask(build.id, TaskProblem.OutsideProject),
            ScheduleChanges.problemWith(state, listOf(ScheduleChange.UpdateTask(build, outside)), zone),
        )
        val tooShort = build.copy(endMillis = build.startMillis + 60_000L)
        assertEquals(
            ChangeProblem.InvalidTask(build.id, TaskProblem.TooShort(15)),
            ScheduleChanges.problemWith(state, listOf(ScheduleChange.UpdateTask(build, tooShort)), zone),
        )
    }

    @Test
    fun deletingATaskRequiresRemovingItsLinks() {
        assertEquals(
            ChangeProblem.BrokenLink(link.id),
            ScheduleChanges.problemWith(state, listOf(ScheduleChange.DeleteTask(design)), zone),
        )
        val withLinks = listOf(ScheduleChange.RemoveLink(link), ScheduleChange.DeleteTask(design))
        assertNull(ScheduleChanges.problemWith(state, withLinks, zone))
    }

    @Test
    fun newLinksMustNotCloseACycleOrPointAtMissingTasks() {
        val back = TaskDependency("l2", SampleTasks.PROJECT_ID, build.id, design.id)
        assertEquals(ChangeProblem.Cycle("l2"), ScheduleChanges.problemWith(state, listOf(ScheduleChange.AddLink(back)), zone))
        val dangling = TaskDependency("l3", SampleTasks.PROJECT_ID, "nope", build.id)
        assertEquals(ChangeProblem.BrokenLink("l3"), ScheduleChanges.problemWith(state, listOf(ScheduleChange.AddLink(dangling)), zone))
        val duplicate = TaskDependency("l4", SampleTasks.PROJECT_ID, design.id, build.id)
        assertEquals(ChangeProblem.Stale(setOf("l4")), ScheduleChanges.problemWith(state, listOf(ScheduleChange.AddLink(duplicate)), zone))
    }

    @Test
    fun createIsStaleWhenTheIdIsTaken() {
        assertEquals(
            ChangeProblem.Stale(setOf(qa.id)),
            ScheduleChanges.problemWith(state, listOf(ScheduleChange.CreateTask(qa)), zone),
        )
    }

    @Test
    fun inverseRestoresTheOriginalScheduleIncludingLinks() {
        val created = SchedulerTask("new", SampleTasks.PROJECT_ID, "New", design.startMillis, design.endMillis, sortOrder = 9)
        val changes = listOf(
            ScheduleChange.UpdateTask(qa, shifted(qa, hour)),
            ScheduleChange.RemoveLink(link),
            ScheduleChange.DeleteTask(design),
            ScheduleChange.CreateTask(created),
            ScheduleChange.AddLink(TaskDependency("l5", SampleTasks.PROJECT_ID, created.id, build.id)),
        )
        assertNull(ScheduleChanges.problemWith(state, changes, zone))
        val after = ScheduleChanges.apply(state, changes).state

        val undo = ScheduleChanges.inverse(changes)
        assertNull(ScheduleChanges.problemWith(after, undo, zone))
        val restored = ScheduleChanges.apply(after, undo)
        assertTrue(restored.staleIds.isEmpty())
        assertEquals(state.tasks.toSet(), restored.state.tasks.toSet())
        assertEquals(state.links.toSet(), restored.state.links.toSet())
    }

    @Test
    fun undoIsRefusedOnceTheTaskWasEditedAgain() {
        val moved = shifted(qa, hour)
        val undo = ScheduleChanges.inverse(listOf(ScheduleChange.UpdateTask(qa, moved)))
        val editedAgain = state.copy(tasks = tasks.map { if (it.id == qa.id) shifted(moved, hour) else it })
        assertEquals(ChangeProblem.Stale(setOf(qa.id)), ScheduleChanges.problemWith(editedAgain, undo, zone))
    }
}
