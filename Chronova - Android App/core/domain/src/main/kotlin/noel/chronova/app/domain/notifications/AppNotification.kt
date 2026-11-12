package noel.chronova.app.domain.notifications

import kotlinx.coroutines.flow.Flow
import noel.chronova.app.domain.schedule.ScheduleChange

enum class NotificationCategory { SYNC, ASSISTANT, ACCOUNT }

/**
 * What happened. [important] ones are also posted to the phone's notification shade.
 * The words are made when shown (from string resources), from the [AppNotification.args]
 * each kind documents.
 */
enum class NotificationKind(val category: NotificationCategory, val important: Boolean = false) {
    /** A sync finished: "sent", "received". */
    SYNCED(NotificationCategory.SYNC),

    /** A sync failed: "reason" (a SyncFailure name). */
    SYNC_FAILED(NotificationCategory.SYNC, important = true),

    /** Back online with unsynced changes: "count". */
    SYNC_WAITING(NotificationCategory.SYNC),

    /** Changes from this phone were older than the server's copy, which was kept: "count". */
    SYNC_CONFLICT(NotificationCategory.SYNC),

    /** The assistant applied a change: "summary" (what it said). */
    ASSISTANT_APPLIED(NotificationCategory.ASSISTANT),

    /** The assistant applied a change the user accepted despite its clashes: "summary", "clashes". */
    ASSISTANT_CLASH(NotificationCategory.ASSISTANT),

    /** "email". */
    SIGNED_IN(NotificationCategory.ACCOUNT),

    /** The server ended the session. */
    SESSION_ENDED(NotificationCategory.ACCOUNT, important = true),

    /** A new password was set; other devices were signed out. */
    PASSWORD_CHANGED(NotificationCategory.ACCOUNT),
}

data class AppNotification(
    val id: Long,
    val kind: NotificationKind,
    val args: Map<String, String>,
    val projectId: String?,
    val projectName: String?,
    val createdAt: Long,
    val read: Boolean,
    /** An assistant change that can still be undone from the notification. */
    val canUndo: Boolean,
)

/** What the notifications page shows: any of the filters, newest or oldest first. */
data class NotificationFilter(
    val category: NotificationCategory? = null,
    val unreadOnly: Boolean = false,
    val projectId: String? = null,
    val newestFirst: Boolean = true,
) {
    fun apply(all: List<AppNotification>): List<AppNotification> {
        val order = compareBy<AppNotification>({ it.createdAt }, { it.id })
        return all
            .filter { category == null || it.kind.category == category }
            .filter { !unreadOnly || !it.read }
            .filter { projectId == null || it.projectId == projectId }
            .sortedWith(if (newestFirst) order.reversed() else order)
    }
}

/** The notifications kept on this phone (cleared with its data on sign-out). */
interface NotificationRepository {
    /** The newest notifications (up to [KEEP]), newest first. */
    fun observe(): Flow<List<AppNotification>>
    fun unreadCount(): Flow<Int>

    suspend fun add(
        kind: NotificationKind,
        args: Map<String, String> = emptyMap(),
        projectId: String? = null,
        projectName: String? = null,
        undo: List<ScheduleChange> = emptyList(),
    )

    suspend fun markRead(id: Long)
    suspend fun markAllRead()
    suspend fun clear()

    /** The changes that undo notification [id], while it still has them. */
    suspend fun undo(id: Long): List<ScheduleChange>?
    suspend fun forgetUndo(id: Long)

    companion object {
        /** How many are kept, and for how long. */
        const val KEEP = 200
        const val KEEP_DAYS = 30L
    }
}
