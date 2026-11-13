package noel.chronova.core.domain.repository

import kotlinx.coroutines.flow.Flow
import noel.chronova.core.model.Project
import noel.chronova.core.model.ProjectSummary
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency

/**
 * Read-only view of projects, tasks and links. ViewModels read through this; every change goes
 * through a use case. [ProjectRepository] extends it, so a ViewModel holding only this can't write.
 */
interface ProjectQueries {
    fun observeProjects(): Flow<List<ProjectSummary>>

    /** Emits null once the project does not exist (or was deleted). */
    fun observeProject(projectId: String): Flow<Project?>

    /** The project's tasks in row order. */
    fun observeTasks(projectId: String): Flow<List<SchedulerTask>>

    /** The project's live links. */
    fun observeDependencies(projectId: String): Flow<List<TaskDependency>>

    /** Every task not yet done, in every live project, earliest first (for reminders). */
    fun observeOpenTasks(): Flow<List<SchedulerTask>>
}
