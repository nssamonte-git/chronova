package noel.chronova.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, TaskEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ChronovaDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): ChronovaDatabase =
            Room.databaseBuilder(context, ChronovaDatabase::class.java, "chronova.db").build()
    }
}
