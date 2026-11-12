package noel.chronova.app.domain.notifications

/** Shows important notifications outside the app (the phone's notification shade). */
fun interface NotificationPoster {
    fun post(notification: AppNotification)
}
