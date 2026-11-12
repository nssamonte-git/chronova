package noel.chronova.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskColor
import noel.chronova.app.domain.model.TaskDependency
import noel.chronova.app.domain.notifications.AppNotification
import noel.chronova.app.domain.notifications.NotificationKind
import noel.chronova.app.domain.notifications.NotificationRepository
import noel.chronova.app.domain.schedule.ScheduleChange

/** One notification on this phone. Not synced; no foreign key, so it outlives a deleted project. */
@Entity(tableName = "notifications", indices = [Index("created_at")])
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** A [NotificationKind] name. */
    val kind: String,
    /** JSON object of strings: the details its kind documents. */
    val args: String,
    @ColumnInfo(name = "project_id") val projectId: String?,
    @ColumnInfo(name = "project_name") val projectName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_read") val read: Boolean,
    /** JSON list of the changes that undo an assistant change; null once used or impossible. */
    val undo: String?,
)

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE is_read = 0")
    fun unreadCount(): Flow<Int>

    @Insert
    suspend fun insert(notification: NotificationEntity): Long

    @Query("UPDATE notifications SET is_read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE notifications SET is_read = 1 WHERE is_read = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications")
    suspend fun clear()

    @Query("SELECT undo FROM notifications WHERE id = :id")
    suspend fun undo(id: Long): String?

    @Query("UPDATE notifications SET undo = NULL WHERE id = :id")
    suspend fun forgetUndo(id: Long)

    /** Keeps the newest [keep] that are no older than [since]. */
    @Query("DELETE FROM notifications WHERE created_at < :since OR id NOT IN (SELECT id FROM notifications ORDER BY created_at DESC, id DESC LIMIT :keep)")
    suspend fun prune(since: Long, keep: Int)
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                "`args` TEXT NOT NULL, `project_id` TEXT, `project_name` TEXT, `created_at` INTEGER NOT NULL, " +
                "`is_read` INTEGER NOT NULL, `undo` TEXT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)")
    }
}

/** Shows important notifications outside the app (the phone's notification shade). */
fun interface NotificationPoster {
    fun post(notification: AppNotification)
}

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
) {
    fun toDomain() = SchedulerTask(id, projectId, title, startMillis, endMillis, TaskColor.valueOf(color), sortOrder)
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

private fun stored(task: SchedulerTask) = StoredTask(task.id, task.projectId, task.title, task.startMillis, task.endMillis, task.color.name, task.sortOrder)

private fun stored(link: TaskDependency) = StoredLink(link.id, link.projectId, link.predecessorId, link.successorId)

private fun toStored(change: ScheduleChange): StoredChange = when (change) {
    is ScheduleChange.UpdateTask -> StoredChange.Update(stored(change.before), stored(change.after))
    is ScheduleChange.CreateTask -> StoredChange.Create(stored(change.task))
    is ScheduleChange.DeleteTask -> StoredChange.Delete(stored(change.task))
    is ScheduleChange.AddLink -> StoredChange.AddLink(stored(change.link))
    is ScheduleChange.RemoveLink -> StoredChange.RemoveLink(stored(change.link))
}
