package noel.chronova.app.presentation.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import noel.chronova.app.domain.notifications.AppNotification
import noel.chronova.app.domain.notifications.NotificationKind
import noel.chronova.app.domain.notifications.NotificationRepository
import noel.chronova.app.domain.schedule.ScheduleChange

/** Notifications in memory; [added] keeps every add in order, with its undo. */
class FakeNotificationRepository : NotificationRepository {
    data class Added(val notification: AppNotification, val undo: List<ScheduleChange>)

    private val rows = MutableStateFlow<List<AppNotification>>(emptyList())
    private val undos = mutableMapOf<Long, List<ScheduleChange>>()
    val added = mutableListOf<Added>()
    private var now = 0L

    override fun observe(): Flow<List<AppNotification>> = rows.map { list -> list.sortedByDescending { it.createdAt } }
    override fun unreadCount(): Flow<Int> = rows.map { list -> list.count { !it.read } }

    override suspend fun add(kind: NotificationKind, args: Map<String, String>, projectId: String?, projectName: String?, undo: List<ScheduleChange>) {
        val notification = AppNotification(added.size + 1L, kind, args, projectId, projectName, ++now, read = false, canUndo = undo.isNotEmpty())
        added += Added(notification, undo)
        if (undo.isNotEmpty()) undos[notification.id] = undo
        rows.value = rows.value + notification
    }

    override suspend fun markRead(id: Long) = update(id) { it.copy(read = true) }
    override suspend fun markAllRead() {
        rows.value = rows.value.map { it.copy(read = true) }
    }
    override suspend fun clear() {
        rows.value = emptyList()
    }
    override suspend fun undo(id: Long): List<ScheduleChange>? = undos[id]
    override suspend fun forgetUndo(id: Long) {
        undos.remove(id)
        update(id) { it.copy(canUndo = false) }
    }

    private fun update(id: Long, change: (AppNotification) -> AppNotification) {
        rows.value = rows.value.map { if (it.id == id) change(it) else it }
    }
}
