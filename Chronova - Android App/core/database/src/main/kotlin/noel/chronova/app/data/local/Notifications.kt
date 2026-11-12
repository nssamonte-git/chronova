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

/** One notification on this phone. Not synced; no foreign key, so it outlives a deleted project. */
@Entity(tableName = "notifications", indices = [Index("created_at")])
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** A `NotificationKind` name (domain). */
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
