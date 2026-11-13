package noel.chronova.feature.notifications.presentation

import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import noel.chronova.core.domain.notifications.AppNotification
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.feature.notifications.domain.NotificationPoster

/**
 * Posts important notifications (a failed sync, an ended session) to the phone's
 * notification shade; tapping one opens the notifications page, or for changes on another
 * device ([OPEN_SYNC]) offers to sync. Without the
 * notification permission (Android 13+) nothing is posted; the page still has them.
 * [openApp] is the intent that opens the app (the shell's activity); [EXTRA_OPEN] is added to it.
 */
class SystemNotifications(
    private val context: Context,
    private val openApp: (Context) -> Intent,
) : NotificationPoster {
    override fun post(notification: AppNotification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notifications_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notifications_channel_body) },
        )
        val text = notificationText(notification.toUi(), context.resources)
        val target = if (notification.kind == NotificationKind.SYNC_AVAILABLE) OPEN_SYNC else OPEN_NOTIFICATIONS
        val open = PendingIntent.getActivity(
            context,
            // One request code per target, so updating one's extras never changes the other's.
            if (target == OPEN_SYNC) 1 else 0,
            openApp(context)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN, target),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val built = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIcon(context))
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(notification.id.toInt(), built)
        } catch (_: SecurityException) {
            // The permission was withdrawn meanwhile.
        }
    }

    companion object {
        const val CHANNEL = "important"
        const val EXTRA_OPEN = "open"
        const val OPEN_NOTIFICATIONS = "notifications"

        /** Opens the app with the "changes on another device" offer to sync. */
        const val OPEN_SYNC = "sync"
    }
}

/**
 * The full-colour app icon, shown beside the notification's text (the status bar can only show the
 * one-colour hourglass). Drawn from the launcher icon, so it is the icon the phone shows for the app.
 */
internal fun appIcon(context: Context): Bitmap? = runCatching {
    val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
    context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap(size, size)
}.getOrNull()
