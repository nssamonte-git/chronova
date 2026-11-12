package noel.chronova.app.domain.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import noel.chronova.app.domain.repository.ProjectRepository
import noel.chronova.app.domain.schedule.ApplyResult
import noel.chronova.app.domain.schedule.ApplyScheduleChanges

/** The notifications on this phone, newest first. */
class ObserveNotifications(private val notifications: NotificationRepository) {
    operator fun invoke(): Flow<List<AppNotification>> = notifications.observe()
}

/** How many notifications are unread (the bell's badge). */
class ObserveUnreadNotificationCount(private val notifications: NotificationRepository) {
    operator fun invoke(): Flow<Int> = notifications.unreadCount()
}

/** Ids of the projects that still exist, so "Open project" isn't offered for a deleted one. */
class ObserveLiveProjectIds(private val projects: ProjectRepository) {
    operator fun invoke(): Flow<Set<String>> = projects.observeProjects().map { list -> list.map { it.project.id }.toSet() }
}

class MarkNotificationRead(private val notifications: NotificationRepository) {
    suspend operator fun invoke(id: Long) = notifications.markRead(id)
}

class MarkAllNotificationsRead(private val notifications: NotificationRepository) {
    suspend operator fun invoke() = notifications.markAllRead()
}

class ClearNotifications(private val notifications: NotificationRepository) {
    suspend operator fun invoke() = notifications.clear()
}

/**
 * Reverses the assistant change a notification records, through the one schedule
 * apply path, so it is refused if its tasks changed since. Either way the
 * notification can't be undone again and is marked read. Returns whether it was undone.
 */
class UndoAssistantChange(
    private val notifications: NotificationRepository,
    private val applyChanges: ApplyScheduleChanges,
) {
    suspend operator fun invoke(notification: AppNotification): Boolean {
        val projectId = notification.projectId
        val changes = notifications.undo(notification.id)
        val undone = projectId != null && changes != null && applyChanges(projectId, changes) is ApplyResult.Applied
        notifications.forgetUndo(notification.id)
        notifications.markRead(notification.id)
        return undone
    }
}

/** Everything the notifications page does. */
class NotificationUseCases(
    val observe: ObserveNotifications,
    val observeLiveProjectIds: ObserveLiveProjectIds,
    val markRead: MarkNotificationRead,
    val markAllRead: MarkAllNotificationsRead,
    val clear: ClearNotifications,
    val undo: UndoAssistantChange,
)
