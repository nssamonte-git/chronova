package noel.chronova.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, TaskEntity::class, ViewSettingsEntity::class, DependencyEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ChronovaDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun viewSettingsDao(): ViewSettingsDao
    abstract fun dependencyDao(): DependencyDao

    companion object {
        fun create(context: Context): ChronovaDatabase =
            Room.databaseBuilder(context, ChronovaDatabase::class.java, "chronova.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
