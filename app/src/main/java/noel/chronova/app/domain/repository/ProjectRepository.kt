package noel.chronova.app.domain.repository

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

    suspend fun updateTask(task: SchedulerTask)

    /** Inserts the sample project the first time the app runs; no-op afterwards. */
    suspend fun seedSampleIfFirstLaunch()
}
