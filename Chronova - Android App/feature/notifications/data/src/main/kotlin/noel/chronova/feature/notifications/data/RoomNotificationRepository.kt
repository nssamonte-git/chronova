package noel.chronova.feature.notifications.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.domain.notifications.AppNotification
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.feature.notifications.domain.NotificationPoster
import noel.chronova.core.domain.notifications.NotificationRepository
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.database.NotificationDao
import noel.chronova.core.database.NotificationEntity

class RoomNotificationRepository(
    private val dao: NotificationDao,
    private val poster: NotificationPoster = NotificationPoster {},
    private val clock: () -> Long = System::currentTimeMillis,
) : NotificationRepository {
    override fun observe(): Flow<List<AppNotification>> =
        dao.observe(NotificationRepository.KEEP).map { rows -> rows.mapNotNull { it.toDomain() } }

    override fun unreadCount(): Flow<Int> = dao.unreadCount()

    override suspend fun add(
        kind: NotificationKind,
        args: Map<String, String>,
        projectId: String?,
        projectName: String?,
        undo: List<ScheduleChange>,
    ) {
        val now = clock()
        val row = NotificationEntity(
            kind = kind.name,
            args = json.encodeToString(ARGS, args),
            projectId = projectId,
            projectName = projectName,
            createdAt = now,
            read = false,
            undo = undo.takeIf { it.isNotEmpty() }?.let { json.encodeToString(UNDO, it.map(::toStored)) },
        )
        val id = dao.insert(row)
        dao.prune(since = now - NotificationRepository.KEEP_DAYS * DAY_MS, keep = NotificationRepository.KEEP)
        if (kind.important) row.copy(id = id).toDomain()?.let(poster::post)
    }

    override suspend fun markRead(id: Long) = dao.markRead(id)
    override suspend fun markAllRead() = dao.markAllRead()
    override suspend fun clear() = dao.clear()

    override suspend fun undo(id: Long): List<ScheduleChange>? =
        dao.undo(id)?.let { stored -> runCatching { json.decodeFromString(UNDO, stored).map(StoredChange::toDomain) }.getOrNull() }

    override suspend fun forgetUndo(id: Long) = dao.forgetUndo(id)

    private fun NotificationEntity.toDomain(): AppNotification? {
        val kind = NotificationKind.entries.firstOrNull { it.name == kind } ?: return null
        return AppNotification(
            id = id,
            kind = kind,
            args = runCatching { json.decodeFromString(ARGS, args) }.getOrDefault(emptyMap()),
            projectId = projectId,
            projectName = projectName,
            createdAt = createdAt,
            read = read,
            canUndo = undo != null,
        )
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        val json = Json { ignoreUnknownKeys = true }
        val ARGS = MapSerializer(String.serializer(), String.serializer())
        val UNDO = ListSerializer(StoredChange.serializer())
    }
}

@Serializable
private data class StoredTask(
    val id: String,
    val projectId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val color: String,
    val sortOrder: Int,
    // Added with task progress, notes and milestones; undos stored before them read as the defaults.
    val progress: Int = 0,
    val notes: String = "",
    val milestone: Boolean = false,
) {
    fun toDomain() = SchedulerTask(id, projectId, title, startMillis, endMillis, TaskColor.valueOf(color), sortOrder, progress, notes, milestone)
}

@Serializable
private data class StoredLink(val id: String, val projectId: String, val predecessorId: String, val successorId: String) {
    fun toDomain() = TaskDependency(id, projectId, predecessorId, successorId)
}

/** A [ScheduleChange] as kept for undo. */
@Serializable
private sealed interface StoredChange {
    fun toDomain(): ScheduleChange

    @Serializable @SerialName("update")
    data class Update(val before: StoredTask, val after: StoredTask) : StoredChange {
        override fun toDomain() = ScheduleChange.UpdateTask(before.toDomain(), after.toDomain())
    }

    @Serializable @SerialName("create")
    data class Create(val task: StoredTask) : StoredChange {
        override fun toDomain() = ScheduleChange.CreateTask(task.toDomain())
    }

    @Serializable @SerialName("delete")
    data class Delete(val task: StoredTask) : StoredChange {
        override fun toDomain() = ScheduleChange.DeleteTask(task.toDomain())
    }

    @Serializable @SerialName("add_link")
    data class AddLink(val link: StoredLink) : StoredChange {
        override fun toDomain() = ScheduleChange.AddLink(link.toDomain())
    }

    @Serializable @SerialName("remove_link")
    data class RemoveLink(val link: StoredLink) : StoredChange {
        override fun toDomain() = ScheduleChange.RemoveLink(link.toDomain())
    }
}

private fun stored(task: SchedulerTask) = StoredTask(
    task.id, task.projectId, task.title, task.startMillis, task.endMillis, task.color.name, task.sortOrder,
    task.progress, task.notes, task.isMilestone,
)

private fun stored(link: TaskDependency) = StoredLink(link.id, link.projectId, link.predecessorId, link.successorId)

private fun toStored(change: ScheduleChange): StoredChange = when (change) {
    is ScheduleChange.UpdateTask -> StoredChange.Update(stored(change.before), stored(change.after))
    is ScheduleChange.CreateTask -> StoredChange.Create(stored(change.task))
    is ScheduleChange.DeleteTask -> StoredChange.Delete(stored(change.task))
    is ScheduleChange.AddLink -> StoredChange.AddLink(stored(change.link))
    is ScheduleChange.RemoveLink -> StoredChange.RemoveLink(stored(change.link))
}
