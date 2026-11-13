package noel.chronova.feature.push.domain

/**
 * A push the server sent (`docs/push.md`): data only, so the app words and posts it itself.
 * Anything this app doesn't understand (a newer server, a malformed message) is null and ignored.
 */
sealed interface PushMessage {
    /** A task (or milestone) is about to start: the same reminder the phone's own alarm would post. */
    data class Reminder(
        val taskId: String,
        val projectId: String,
        val projectName: String,
        val title: String,
        val isMilestone: Boolean,
        val startMillis: Long,
        val atMillis: Long,
    ) : PushMessage

    /** Another of the user's devices synced changes. */
    data object SyncAvailable : PushMessage

    /** Someone signed in to the account on another device. */
    data object SignedIn : PushMessage

    /** The password changed or a session was ended elsewhere: the app checks whether it is still signed in. */
    data object AccountChanged : PushMessage

    companion object {
        fun parse(data: Map<String, String>): PushMessage? = when (data["type"]) {
            "reminder" -> reminder(data)
            "sync_available" -> SyncAvailable
            "signed_in" -> SignedIn
            "account_changed" -> AccountChanged
            else -> null
        }

        private fun reminder(data: Map<String, String>): Reminder? {
            val taskId = data["taskId"]?.takeIf { it.isNotBlank() } ?: return null
            val projectId = data["projectId"]?.takeIf { it.isNotBlank() } ?: return null
            val title = data["title"] ?: return null
            val start = data["startMillis"]?.toLongOrNull() ?: return null
            val at = data["atMillis"]?.toLongOrNull() ?: return null
            return Reminder(taskId, projectId, data["projectName"].orEmpty(), title, data["milestone"] == "true", start, at)
        }
    }
}
