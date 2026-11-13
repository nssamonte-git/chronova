package noel.chronova.feature.reminders.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import noel.chronova.feature.reminders.domain.Reminder
import noel.chronova.feature.reminders.domain.ReminderAlarm
import noel.chronova.feature.reminders.domain.ReminderLog
import noel.chronova.feature.reminders.domain.ReminderPoster

/**
 * One inexact alarm for the next reminder (`setAndAllowWhileIdle`): it needs no special
 * permission and still goes off while the phone dozes, though Android may hold it back a
 * few minutes then.
 */
class AlarmManagerReminderAlarm(private val context: Context) : ReminderAlarm {
    override fun set(atMillis: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (atMillis == null) alarms.cancel(operation) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
    }
}

/** How far reminders have been handled, in its own preferences file. */
class PreferencesReminderLog(private val prefs: SharedPreferences) : ReminderLog {
    override var handledUpToMillis: Long
        get() = prefs.getLong(KEY, 0L)
        set(value) = prefs.edit { putLong(KEY, value) }

    private companion object {
        const val KEY = "handled_up_to"
    }
}

/**
 * Reminders in their own channel ("Task reminders", so they can be muted apart from the
 * important ones): the task's title, then when it starts and its project. Tapping one
 * opens that project ([openProject] builds the shell's intent). Without the notification
 * permission (Android 13+) nothing is posted.
 */
class NotificationReminderPoster(
    private val context: Context,
    private val openProject: (Context, projectId: String) -> Intent,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ReminderPoster {
    override fun post(reminders: List<Reminder>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.reminders_channel_body) },
        )
        val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(zone())
        for (reminder in reminders) {
            val whenText = format.format(Instant.ofEpochMilli(reminder.startMillis))
            val body = context.getString(
                if (reminder.isMilestone) R.string.reminder_milestone else R.string.reminder_task,
                whenText,
                reminder.projectName,
            )
            val open = PendingIntent.getActivity(
                context,
                reminder.projectId.hashCode(),
                openProject(context, reminder.projectId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(reminder.title)
                .setContentText(body)
                .setWhen(reminder.startMillis)
                .setShowWhen(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify((reminder.taskId + reminder.atMillis).hashCode(), notification)
            } catch (_: SecurityException) {
                return // the permission was withdrawn meanwhile
            }
        }
    }

    companion object {
        const val CHANNEL = "reminders"
    }
}
