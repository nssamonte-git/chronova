package noel.chronova.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** How a project's timeline was last viewed on this device. Local only; never synced. */
@Entity(
    tableName = "view_settings",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ViewSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "project_id") val projectId: String,
    /** [noel.chronova.app.domain.model.TimeScale] name. */
    val scale: String,
    @ColumnInfo(name = "unit_width_dp") val unitWidthDp: Float,
    @ColumnInfo(name = "snap_minutes") val snapMinutes: Int,
    @ColumnInfo(name = "row_height_dp") val rowHeightDp: Float,
)

@Dao
interface ViewSettingsDao {
    @Query("SELECT * FROM view_settings WHERE project_id = :projectId")
    suspend fun get(projectId: String): ViewSettingsEntity?

    @Upsert
    suspend fun upsert(settings: ViewSettingsEntity)
}

/** v1 → v2: adds `view_settings`. Must match the schema Room exports for v2. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `view_settings` (
                `project_id` TEXT NOT NULL,
                `scale` TEXT NOT NULL,
                `unit_width_dp` REAL NOT NULL,
                `snap_minutes` INTEGER NOT NULL,
                `row_height_dp` REAL NOT NULL,
                PRIMARY KEY(`project_id`),
                FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}
