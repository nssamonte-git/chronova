package noel.chronova.feature.notifications.domain

import noel.chronova.core.domain.notifications.AppNotification

/** Shows important notifications outside the app (the phone's notification shade). */
fun interface NotificationPoster {
    fun post(notification: AppNotification)
}
