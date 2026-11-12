package noel.chronova.app.presentation.notifications

import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.notifications.NotificationCategory
import noel.chronova.app.domain.notifications.NotificationKind
import noel.chronova.app.domain.notifications.NotificationUseCases
import noel.chronova.app.domain.schedule.ApplyScheduleChanges
import noel.chronova.app.domain.schedule.ScheduleChange
import noel.chronova.app.testing.FakeProjectRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import noel.chronova.app.testing.FakeNotificationRepository
import noel.chronova.app.domain.notifications.ObserveNotifications
import noel.chronova.app.domain.notifications.ObserveLiveProjectIds
import noel.chronova.app.domain.notifications.MarkNotificationRead
import noel.chronova.app.domain.notifications.MarkAllNotificationsRead
import noel.chronova.app.domain.notifications.ClearNotifications
import noel.chronova.app.domain.notifications.UndoAssistantChange
import noel.chronova.app.domain.notifications.NotificationRepository
import noel.chronova.app.domain.repository.ProjectRepository
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val zone = ZoneOffset.UTC
    private val dispatcher = StandardTestDispatcher()
    private val projects = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone))
    private val notifications = FakeNotificationRepository()
    private lateinit var viewModel: NotificationsViewModel

    private val review get() = projects.tasks.value.first { it.id == "review" }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = NotificationsViewModel(notificationUseCases(notifications, projects, zone))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun filtersSortsAndKnowsWhichProjectsAreMentioned() = runTest(dispatcher) {
        notifications.add(NotificationKind.SYNCED)
        notifications.add(NotificationKind.ASSISTANT_APPLIED, projectId = SampleTasks.PROJECT_ID, projectName = "Sample project")
        notifications.add(NotificationKind.SESSION_ENDED)
        advanceUntilIdle()
        assertEquals(3, viewModel.shown.value.size)

        viewModel.setCategory(NotificationCategory.ASSISTANT)
        advanceUntilIdle()
        assertEquals(listOf(NotificationKind.ASSISTANT_APPLIED), viewModel.shown.value.map { it.kind })
        assertEquals(listOf(ProjectChoice(SampleTasks.PROJECT_ID, "Sample project")), viewModel.projectChoices.value)
        assertTrue(SampleTasks.PROJECT_ID in viewModel.liveProjectIds.value)

        viewModel.setCategory(null)
        viewModel.toggleOrder()
        advanceUntilIdle()
        assertEquals(listOf(NotificationKind.SYNCED, NotificationKind.ASSISTANT_APPLIED, NotificationKind.SESSION_ENDED), viewModel.shown.value.map { it.kind })

        viewModel.markRead(viewModel.shown.value.first().id)
        viewModel.toggleUnreadOnly()
        advanceUntilIdle()
        assertEquals(2, viewModel.shown.value.size)
        viewModel.markAllRead()
        advanceUntilIdle()
        assertTrue(viewModel.shown.value.isEmpty())
        assertFalse(viewModel.isEmpty.value) // filtered out, not gone
    }

    @Test
    fun undoReversesTheAssistantsChangeOnce() = runTest(dispatcher) {
        val original = review
        val moved = original.copy(startMillis = original.startMillis + 3_600_000L, endMillis = original.endMillis + 3_600_000L)
        projects.tasks.value = projects.tasks.value.map { if (it.id == "review") moved else it }
        notifications.add(
            NotificationKind.ASSISTANT_APPLIED,
            projectId = SampleTasks.PROJECT_ID,
            undo = listOf(ScheduleChange.UpdateTask(moved, original)),
        )
        advanceUntilIdle()
        viewModel.undo(viewModel.shown.value.single())
        advanceUntilIdle()

        assertEquals(original, review)
        assertEquals(NotificationsMessage.UNDONE, viewModel.message.value)
        assertFalse(viewModel.shown.value.single().canUndo)
        assertTrue(viewModel.shown.value.single().read)
    }

    @Test
    fun undoIsRefusedWhenTheTaskChangedSince() = runTest(dispatcher) {
        val original = review
        val movedThen = original.copy(startMillis = original.startMillis + 3_600_000L, endMillis = original.endMillis + 3_600_000L)
        // The task was moved again afterwards, so the undo no longer fits.
        notifications.add(NotificationKind.ASSISTANT_APPLIED, projectId = SampleTasks.PROJECT_ID, undo = listOf(ScheduleChange.UpdateTask(movedThen, original.copy(title = "x"))))
        advanceUntilIdle()
        viewModel.undo(viewModel.shown.value.single())
        advanceUntilIdle()

        assertEquals(original, review)
        assertEquals(NotificationsMessage.CANNOT_UNDO, viewModel.message.value)
        assertFalse(viewModel.shown.value.single().canUndo)
    }
}

/** The use cases the app wires, over fake repositories. */
private fun notificationUseCases(notifications: NotificationRepository, projects: ProjectRepository, zone: ZoneId) = NotificationUseCases(
    observe = ObserveNotifications(notifications),
    observeLiveProjectIds = ObserveLiveProjectIds(projects),
    markRead = MarkNotificationRead(notifications),
    markAllRead = MarkAllNotificationsRead(notifications),
    clear = ClearNotifications(notifications),
    undo = UndoAssistantChange(notifications, ApplyScheduleChanges(projects, zone)),
)
