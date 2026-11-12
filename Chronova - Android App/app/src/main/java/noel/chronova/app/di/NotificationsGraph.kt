package noel.chronova.app.di

import java.time.ZoneId
import noel.chronova.app.AppContainer
import noel.chronova.core.domain.notifications.NotificationQueries
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.feature.notifications.domain.ClearNotifications
import noel.chronova.feature.notifications.domain.MarkAllNotificationsRead
import noel.chronova.feature.notifications.domain.MarkNotificationRead
import noel.chronova.feature.notifications.domain.NotificationUseCases
import noel.chronova.feature.notifications.domain.UndoAssistantChange

/** The notifications page's and the bell's wiring. */
class NotificationsGraph(private val app: AppContainer) {
    /** What the page and the bell read (writes go through the use cases). */
    val queries: NotificationQueries get() = app.notificationRepository

    /** The time zone is read when the page opens. */
    fun useCases(): NotificationUseCases = NotificationUseCases(
        markRead = MarkNotificationRead(app.notificationRepository),
        markAllRead = MarkAllNotificationsRead(app.notificationRepository),
        clear = ClearNotifications(app.notificationRepository),
        undo = UndoAssistantChange(app.notificationRepository, ApplyScheduleChanges(app.projectRepository, ZoneId.systemDefault())),
    )
}
