package noel.chronova.core.domain.repository

import kotlinx.coroutines.flow.Flow
import noel.chronova.core.model.Project
import noel.chronova.core.model.ProjectSummary
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.model.TaskOutline

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

    /** Every live task of every live project, as its place and colour only, in row order within its project (for the list's small charts). */
    fun observeTaskOutlines(): Flow<List<TaskOutline>>

    /** Every task not yet done, in every live project, earliest first (for reminders). */
    fun observeOpenTasks(): Flow<List<SchedulerTask>>
}
