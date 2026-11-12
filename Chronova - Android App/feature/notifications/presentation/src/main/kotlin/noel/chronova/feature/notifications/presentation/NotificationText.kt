package noel.chronova.feature.notifications.presentation

import android.content.res.Resources
import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.domain.notifications.AppNotification
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.ui.R as CoreUiR

data class NotificationText(val title: String, val body: String)

/** A notification's words, in the phone's language, for the page and the notification shade. */
fun notificationText(notification: AppNotification, resources: Resources): NotificationText {
    val args = notification.args
    fun count(key: String) = args[key]?.toIntOrNull() ?: 0
    fun tasks() = args["tasks"].orEmpty().split('\n').filter { it.isNotBlank() }.joinToString(", ")
    return when (notification.kind) {
        NotificationKind.SYNCED -> NotificationText(
            resources.getString(R.string.notif_synced),
            resources.getQuantityString(R.plurals.notif_sent, count("sent"), count("sent")) + " · " +
                resources.getQuantityString(R.plurals.notif_received, count("received"), count("received")),
        )
        NotificationKind.SYNC_FAILED -> NotificationText(
            resources.getString(R.string.notif_sync_failed),
            resources.getString(
                when (args["reason"]?.let { name -> SyncFailure.entries.firstOrNull { it.name == name } }) {
                    SyncFailure.OFFLINE -> R.string.notif_sync_failed_offline
                    SyncFailure.BUSY -> R.string.notif_sync_failed_busy
                    SyncFailure.SIGN_IN_AGAIN, SyncFailure.NOT_SIGNED_IN -> R.string.notif_sync_failed_sign_in
                    SyncFailure.SERVER, null -> R.string.notif_sync_failed_server
                },
            ),
        )
        NotificationKind.SYNC_WAITING -> NotificationText(
            resources.getString(R.string.notif_waiting),
            resources.getQuantityString(R.plurals.notif_waiting_body, count("count"), count("count")),
        )
        NotificationKind.SYNC_CONFLICT -> NotificationText(
            resources.getString(R.string.notif_conflict),
            resources.getQuantityString(R.plurals.notif_conflict_body, count("count"), count("count")),
        )
        NotificationKind.ASSISTANT_APPLIED -> NotificationText(
            notification.projectName?.let { resources.getString(R.string.notif_assistant, it) } ?: resources.getString(R.string.notif_assistant_any),
            resources.getQuantityString(R.plurals.notif_assistant_body, count("count"), count("count"), tasks()),
        )
        NotificationKind.ASSISTANT_CLASH -> NotificationText(
            resources.getString(R.string.notif_clash),
            resources.getString(
                if (args["clash"] == "link") R.string.notif_clash_link else R.string.notif_clash_overlap,
                args["first"].orEmpty(),
                args["second"].orEmpty(),
            ) + " · " + resources.getQuantityString(R.plurals.notif_assistant_body, count("count"), count("count"), tasks()),
        )
        NotificationKind.SIGNED_IN -> NotificationText(
            resources.getString(R.string.notif_signed_in),
            resources.getString(R.string.notif_signed_in_body, args["email"].orEmpty()),
        )
        NotificationKind.SESSION_ENDED -> NotificationText(
            resources.getString(R.string.notif_session_ended),
            resources.getString(CoreUiR.string.account_session_ended),
        )
        NotificationKind.PASSWORD_CHANGED -> NotificationText(
            resources.getString(R.string.notif_password_changed),
            resources.getString(R.string.notif_password_changed_body),
        )
    }
}
