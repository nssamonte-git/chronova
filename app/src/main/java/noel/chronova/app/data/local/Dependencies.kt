package noel.chronova.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Finish-to-start link between two tasks of a project. The task ids are not
 * foreign keys: a sync may deliver a link before its tasks, and tasks are only
 * ever tombstoned, never removed while links exist.
 */
@Entity(
    tableName = "dependencies",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("project_id"), Index("predecessor_id"), Index("successor_id")],
)
data class DependencyEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "predecessor_id") val predecessorId: String,
    @ColumnInfo(name = "successor_id") val successorId: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Dao
interface DependencyDao {
    @Query("SELECT * FROM dependencies WHERE project_id = :projectId AND deleted = 0")
    fun observe(projectId: String): Flow<List<DependencyEntity>>

    @Query("SELECT * FROM dependencies WHERE project_id = :projectId AND deleted = 0")
    suspend fun live(projectId: String): List<DependencyEntity>

    @Query("UPDATE dependencies SET deleted = 1, dirty = 1, updated_at = :now WHERE id IN (:ids)")
    suspend fun markDeleted(ids: List<String>, now: Long)

    @Query("UPDATE dependencies SET deleted = 1, dirty = 1, updated_at = :now WHERE project_id = :projectId AND deleted = 0")
    suspend fun markDeletedInProject(projectId: String, now: Long)

    @Upsert
    suspend fun upsertAll(dependencies: List<DependencyEntity>)
}

/** v2 → v3: adds `dependencies`. Must match the schema Room exports for v3. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `dependencies` (`id` TEXT NOT NULL, `project_id` TEXT NOT NULL, " +
                "`predecessor_id` TEXT NOT NULL, `successor_id` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dependencies_project_id` ON `dependencies` (`project_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dependencies_predecessor_id` ON `dependencies` (`predecessor_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dependencies_successor_id` ON `dependencies` (`successor_id`)")
    }
}
