package noel.chronova.feature.notifications.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import noel.chronova.core.domain.repository.ProjectRepository
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.domain.notifications.AppNotification
import noel.chronova.core.domain.notifications.NotificationRepository

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
    val markRead: MarkNotificationRead,
    val markAllRead: MarkAllNotificationsRead,
    val clear: ClearNotifications,
    val undo: UndoAssistantChange,
)
