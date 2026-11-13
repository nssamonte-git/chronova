package noel.chronova.feature.reminders.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import noel.chronova.feature.reminders.domain.ReminderEngine

/** The reminder alarm went off: post what is due and set the next alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runEngine(context) { onAlarm() }
}

/** After a restart, an app update or a clock or time-zone change, the alarm is set again. */
class RescheduleRemindersReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in ACTIONS) runEngine(context) { reschedule() }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

/** Runs [work] off the main thread, keeping the receiver alive until it finishes. */
private fun BroadcastReceiver.runEngine(context: Context, work: suspend ReminderEngine.() -> Unit) {
    val host = context.applicationContext as? ReminderHost ?: return
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            host.reminderEngine.work()
        } finally {
            pending.finish()
        }
    }
}
