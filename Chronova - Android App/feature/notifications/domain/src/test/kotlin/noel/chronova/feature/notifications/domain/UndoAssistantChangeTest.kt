package noel.chronova.feature.notifications.domain

import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.testing.FakeNotificationRepository
import noel.chronova.core.testing.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import noel.chronova.core.domain.notifications.AppNotification
import noel.chronova.core.domain.notifications.NotificationKind

class UndoAssistantChangeTest {
    private val zone = ZoneOffset.UTC
    private val original = SampleTasks.tasks(zone).first { it.id == "review" }
    private val moved = original.copy(startMillis = original.startMillis + 3_600_000L, endMillis = original.endMillis + 3_600_000L)
    private val projects = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone).map { if (it.id == "review") moved else it })
    private val notifications = FakeNotificationRepository()
    private val undo = UndoAssistantChange(notifications, ApplyScheduleChanges(projects, zone))

    private suspend fun recorded(): AppNotification {
        notifications.add(NotificationKind.ASSISTANT_APPLIED, emptyMap(), SampleTasks.PROJECT_ID, "Sample project", listOf(ScheduleChange.UpdateTask(moved, original)))
        return notifications.observe().first().single()
    }

    private val review get() = projects.tasks.value.first { it.id == "review" }

    @Test
    fun undoesTheChangeOnceAndMarksItRead() = runTest {
        val notification = recorded()
        assertTrue(undo(notification))
        assertEquals(original, review)
        val after = notifications.observe().first().single()
        assertFalse(after.canUndo)
        assertTrue(after.read)
    }

    @Test
    fun aTaskEditedSinceIsLeftAloneButTheUndoIsStillUsedUp() = runTest {
        val notification = recorded()
        val editedAgain = moved.copy(title = "Final review")
        projects.tasks.value = projects.tasks.value.map { if (it.id == "review") editedAgain else it }

        assertFalse(undo(notification))
        assertEquals(editedAgain, review)
        val after = notifications.observe().first().single()
        assertFalse(after.canUndo)
        assertTrue(after.read)
    }
}
