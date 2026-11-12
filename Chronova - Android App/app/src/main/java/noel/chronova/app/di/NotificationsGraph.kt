package noel.chronova.app.di

import java.time.ZoneId
import noel.chronova.app.AppContainer
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.feature.notifications.domain.ClearNotifications
import noel.chronova.feature.notifications.domain.MarkAllNotificationsRead
import noel.chronova.feature.notifications.domain.MarkNotificationRead
import noel.chronova.feature.notifications.domain.NotificationUseCases
import noel.chronova.feature.notifications.domain.ObserveLiveProjectIds
import noel.chronova.feature.notifications.domain.ObserveNotifications
import noel.chronova.feature.notifications.domain.ObserveUnreadNotificationCount
import noel.chronova.feature.notifications.domain.UndoAssistantChange

/** The notifications page's and the bell's wiring. */
class NotificationsGraph(private val app: AppContainer) {
    /** The bell's unread count. */
    val observeUnreadCount = ObserveUnreadNotificationCount(app.notificationRepository)

    /** The time zone is read when the page opens. */
    fun useCases(): NotificationUseCases = NotificationUseCases(
        observe = ObserveNotifications(app.notificationRepository),
        observeLiveProjectIds = ObserveLiveProjectIds(app.projectRepository),
        markRead = MarkNotificationRead(app.notificationRepository),
        markAllRead = MarkAllNotificationsRead(app.notificationRepository),
        clear = ClearNotifications(app.notificationRepository),
        undo = UndoAssistantChange(app.notificationRepository, ApplyScheduleChanges(app.projectRepository, ZoneId.systemDefault())),
    )
}
