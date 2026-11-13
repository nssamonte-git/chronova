package noel.chronova.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Sync bookkeeping shared by every synced table:
 * - [updatedAt]: epoch millis of the last local edit (last-write-wins clock).
 * - [deleted]: tombstone; the row is hidden but kept until the delete is synced.
 * - [dirty]: changed locally since the last successful sync.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** ISO-8601 local date, e.g. 2026-03-02. */
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("project_id")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    val title: String,
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long,
    /** [noel.chronova.core.model.TaskColor] name. */
    val color: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
    /** Percent complete, 0–100 (added in v5, see [MIGRATION_4_5]). */
    @ColumnInfo(defaultValue = "0") val progress: Int = 0,
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    @ColumnInfo(name = "is_milestone", defaultValue = "0") val isMilestone: Boolean = false,
)

/** v5: task progress, notes and milestones; existing tasks become not started, no notes, not milestones. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `progress` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `is_milestone` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Row of the task-outline query: a live task's place and colour, without its title or notes. */
data class TaskOutlineRow(
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long,
    val color: String,
    @ColumnInfo(name = "is_milestone") val isMilestone: Boolean,
)

/** Row of the project-list query: a live project with its live task count and how many are done. */
data class ProjectWithTaskCount(
    val id: String,
    val name: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "task_count") val taskCount: Int,
    @ColumnInfo(name = "done_count") val doneCount: Int,
)
