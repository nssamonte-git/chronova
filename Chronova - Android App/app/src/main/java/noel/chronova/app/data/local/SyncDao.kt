package noel.chronova.app.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Queries used only by sync: dirty rows (tombstones included) and clean-marking. */
@Dao
interface SyncDao {
    @Query(
        "SELECT (SELECT COUNT(*) FROM projects WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM tasks WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM dependencies WHERE dirty = 1)",
    )
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM projects WHERE dirty = 1")
    suspend fun dirtyProjects(): List<ProjectEntity>

    @Query("SELECT * FROM tasks WHERE dirty = 1")
    suspend fun dirtyTasks(): List<TaskEntity>

    @Query("SELECT * FROM dependencies WHERE dirty = 1")
    suspend fun dirtyDependencies(): List<DependencyEntity>

    // Clears `dirty` only if the row wasn't edited again while the push was in flight.
    @Query("UPDATE projects SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun markProjectClean(id: String, updatedAt: Long)

    @Query("UPDATE tasks SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun markTaskClean(id: String, updatedAt: Long)

    @Query("UPDATE dependencies SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun markDependencyClean(id: String, updatedAt: Long)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun project(id: String): ProjectEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: String): TaskEntity?

    @Query("SELECT * FROM dependencies WHERE id = :id")
    suspend fun dependency(id: String): DependencyEntity?

    @androidx.room.Upsert
    suspend fun upsertProject(project: ProjectEntity)

    @androidx.room.Upsert
    suspend fun upsertTask(task: TaskEntity)

    @androidx.room.Upsert
    suspend fun upsertDependency(dependency: DependencyEntity)
}
