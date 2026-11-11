package noel.chronova.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query(
        """
        SELECT p.id, p.name, p.start_date, p.end_date,
               (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.deleted = 0) AS task_count
        FROM projects p
        WHERE p.deleted = 0
        ORDER BY p.start_date DESC, p.name COLLATE NOCASE
        """,
    )
    fun observeProjects(): Flow<List<ProjectWithTaskCount>>

    @Query("SELECT * FROM projects WHERE id = :id AND deleted = 0")
    fun observeProject(id: String): Flow<ProjectEntity?>

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("UPDATE projects SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE project_id = :projectId AND deleted = 0 ORDER BY sort_order, start_millis")
    fun observeTasks(projectId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE project_id = :projectId AND deleted = 0 ORDER BY sort_order, start_millis")
    suspend fun tasks(projectId: String): List<TaskEntity>

    @Query("UPDATE tasks SET deleted = 1, dirty = 1, updated_at = :now WHERE project_id = :projectId AND deleted = 0")
    suspend fun markDeletedInProject(projectId: String, now: Long)

    /** Highest row position ever used in the project, including deleted rows (so undo keeps its row). */
    @Query("SELECT MAX(sort_order) FROM tasks WHERE project_id = :projectId")
    suspend fun maxSortOrder(projectId: String): Int?

    @Query("UPDATE tasks SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)
}
