package noel.chronova.app.domain.repository

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.ProjectSummary
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskColor
import noel.chronova.app.domain.model.TaskDependency
import noel.chronova.app.domain.model.TimelineConfig
import noel.chronova.app.domain.schedule.ScheduleChange
import noel.chronova.app.domain.schedule.ScheduleState

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

    /** Creates a task in the project's last row and returns its new id. */
    suspend fun createTask(
        projectId: String,
        title: String,
        startMillis: Long,
        endMillis: Long,
        color: TaskColor,
    ): String

    /** Saves an edited task (from the task editor). */
    suspend fun updateTask(task: SchedulerTask)

    /**
     * In one transaction: reads the project's live schedule (null if the project
     * is gone) and passes it to [verify]. When [verify] returns null, [changes] are
     * written in order and null is returned; otherwise nothing is written and
     * [verify]'s result is returned. Use [noel.chronova.app.domain.schedule.ApplyScheduleChanges].
     */
    suspend fun <P : Any> applyChanges(
        projectId: String,
        changes: List<ScheduleChange>,
        verify: (ScheduleState?) -> P?,
    ): P?

    /** The project's live links. */
    fun observeDependencies(projectId: String): Flow<List<TaskDependency>>

    /**
     * Makes [predecessorIds] exactly the tasks that [taskId] depends on: missing
     * links are added and others removed. Callers check [noel.chronova.app.domain.DependencyRules].
     */
    suspend fun setPredecessors(projectId: String, taskId: String, predecessorIds: Set<String>)

    /** How this project's timeline was last viewed on this device, or null if never. */
    suspend fun viewSettings(projectId: String): TimelineConfig?

    suspend fun saveViewSettings(projectId: String, config: TimelineConfig)

    /** Inserts the sample project the first time the app runs; no-op afterwards. */
    suspend fun seedSampleIfFirstLaunch()
}
