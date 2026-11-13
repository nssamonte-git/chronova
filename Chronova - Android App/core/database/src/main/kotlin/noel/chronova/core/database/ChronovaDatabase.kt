package noel.chronova.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, TaskEntity::class, ViewSettingsEntity::class, DependencyEntity::class, NotificationEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class ChronovaDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun viewSettingsDao(): ViewSettingsDao
    abstract fun dependencyDao(): DependencyDao
    abstract fun syncDao(): SyncDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        fun create(context: Context): ChronovaDatabase =
            Room.databaseBuilder(context, ChronovaDatabase::class.java, "chronova.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
