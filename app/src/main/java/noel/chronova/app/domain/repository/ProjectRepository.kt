package noel.chronova.app.domain.repository

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.ProjectSummary
import noel.chronova.app.domain.model.SchedulerTask

/**
 * Local source of truth for projects and their tasks. Every write is local first;
 * syncing to the server is a separate, user-confirmed step.
 */
interface ProjectRepository {
    fun observeProjects(): Flow<List<ProjectSummary>>

    /** Emits null once the project does not exist (or was deleted). */
    fun observeProject(projectId: String): Flow<Project?>

    /** The project's tasks in row order. */
    fun observeTasks(projectId: String): Flow<List<SchedulerTask>>

    /** The project's live tasks right now (for validation). */
    suspend fun tasksOf(projectId: String): List<SchedulerTask>

    /** Creates a project and returns its new id. */
    suspend fun createProject(name: String, startDate: LocalDate, endDate: LocalDate): String

    suspend fun updateProject(project: Project)

    /** Deletes the project and its tasks (as tombstones until synced). */
    suspend fun deleteProject(projectId: String)

    suspend fun updateTask(task: SchedulerTask)

    /** Inserts the sample project the first time the app runs; no-op afterwards. */
    suspend fun seedSampleIfFirstLaunch()
}
