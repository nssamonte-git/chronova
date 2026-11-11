package noel.chronova.app.data.repository

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import noel.chronova.app.data.local.ChronovaDatabase
import noel.chronova.app.data.local.ViewSettingsEntity
import noel.chronova.app.data.local.toDomain
import noel.chronova.app.data.local.toEntity
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.ProjectSummary
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskColor
import noel.chronova.app.domain.model.TaskDependency
import noel.chronova.app.domain.model.TimelineConfig
import noel.chronova.app.domain.repository.ProjectRepository
import noel.chronova.app.domain.schedule.ScheduleChange
import noel.chronova.app.domain.schedule.ScheduleState

/**
 * Room-backed [ProjectRepository]. Every write stamps `updatedAt` from [clock] and
 * marks the row dirty, so a later sync knows what changed locally.
 */
class RoomProjectRepository(
    private val db: ChronovaDatabase,
    private val firstLaunch: FirstLaunchFlag,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ProjectRepository {

    override fun observeProjects(): Flow<List<ProjectSummary>> =
        db.projectDao().observeProjects().map { rows -> rows.map { it.toDomain() } }

    override fun observeProject(projectId: String): Flow<Project?> =
        db.projectDao().observeProject(projectId).map { it?.toDomain() }

    override fun observeTasks(projectId: String): Flow<List<SchedulerTask>> =
        db.taskDao().observeTasks(projectId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun tasksOf(projectId: String): List<SchedulerTask> =
        db.taskDao().tasks(projectId).map { it.toDomain() }

    override suspend fun createProject(name: String, startDate: LocalDate, endDate: LocalDate): String {
        val project = Project(newId(), name.trim(), startDate, endDate)
        db.projectDao().upsert(project.toEntity(clock()))
        return project.id
    }

    override suspend fun updateProject(project: Project) {
        db.projectDao().upsert(project.copy(name = project.name.trim()).toEntity(clock()))
    }

    override suspend fun deleteProject(projectId: String) {
        val now = clock()
        db.withTransaction {
            db.dependencyDao().markDeletedInProject(projectId, now)
            db.taskDao().markDeletedInProject(projectId, now)
            db.projectDao().markDeleted(projectId, now)
        }
    }

    override suspend fun createTask(
        projectId: String,
        title: String,
        startMillis: Long,
        endMillis: Long,
        color: TaskColor,
    ): String = db.withTransaction {
        val task = SchedulerTask(
            id = newId(),
            projectId = projectId,
            title = title.trim(),
            startMillis = startMillis,
            endMillis = endMillis,
            color = color,
            sortOrder = (db.taskDao().maxSortOrder(projectId) ?: -1) + 1,
        )
        db.taskDao().upsert(task.toEntity(clock()))
        task.id
    }

    override suspend fun updateTask(task: SchedulerTask) {
        db.taskDao().upsert(task.copy(title = task.title.trim()).toEntity(clock()))
    }

    override suspend fun schedule(projectId: String): ScheduleState? = db.withTransaction { readSchedule(projectId) }

    private suspend fun readSchedule(projectId: String): ScheduleState? = db.projectDao().project(projectId)?.let { project ->
        ScheduleState(
            project = project.toDomain(),
            tasks = db.taskDao().tasks(projectId).map { it.toDomain() },
            links = db.dependencyDao().live(projectId).map { it.toDomain() },
        )
    }

    override suspend fun <P : Any> applyChanges(
        projectId: String,
        changes: List<ScheduleChange>,
        verify: (ScheduleState?) -> P?,
    ): P? = db.withTransaction {
        val current = readSchedule(projectId)
        val problem = verify(current)
        // Written exactly as given (no trimming), so the undo changes match what is stored.
        if (problem == null) {
            val now = clock()
            for (change in changes) {
                when (change) {
                    is ScheduleChange.UpdateTask -> db.taskDao().upsert(change.after.toEntity(now))
                    // Upserting also restores a tombstoned task (undo of a delete).
                    is ScheduleChange.CreateTask -> db.taskDao().upsert(change.task.toEntity(now))
                    is ScheduleChange.DeleteTask -> db.taskDao().markDeleted(change.task.id, now)
                    is ScheduleChange.AddLink -> db.dependencyDao().upsertAll(listOf(change.link.toEntity(now)))
                    is ScheduleChange.RemoveLink -> db.dependencyDao().markDeleted(listOf(change.link.id), now)
                }
            }
        }
        problem
    }

    override fun observeDependencies(projectId: String): Flow<List<TaskDependency>> =
        db.dependencyDao().observe(projectId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun setPredecessors(projectId: String, taskId: String, predecessorIds: Set<String>) {
        db.withTransaction {
            val now = clock()
            val current = db.dependencyDao().live(projectId).filter { it.successorId == taskId }
            val removed = current.filter { it.predecessorId !in predecessorIds }
            if (removed.isNotEmpty()) db.dependencyDao().markDeleted(removed.map { it.id }, now)
            val existing = current.map { it.predecessorId }.toSet()
            val added = (predecessorIds - existing).map { TaskDependency(newId(), projectId, it, taskId).toEntity(now) }
            if (added.isNotEmpty()) db.dependencyDao().upsertAll(added)
        }
    }

    override suspend fun viewSettings(projectId: String): TimelineConfig? =
        db.viewSettingsDao().get(projectId)?.let {
            TimelineConfig.restore(it.scale, it.unitWidthDp, it.snapMinutes, it.rowHeightDp)
        }

    override suspend fun saveViewSettings(projectId: String, config: TimelineConfig) {
        db.viewSettingsDao().upsert(
            ViewSettingsEntity(projectId, config.timeScale.name, config.unitWidthDp, config.snapMinutes, config.rowHeightDp),
        )
    }

    override suspend fun seedSampleIfFirstLaunch() {
        if (!firstLaunch.isFirst()) return
        val now = clock()
        db.withTransaction {
            db.projectDao().upsert(SampleTasks.project.toEntity(now))
            db.taskDao().upsertAll(SampleTasks.tasks(zone()).map { it.toEntity(now) })
        }
        firstLaunch.markDone()
    }
}

/** Remembers whether the first-launch seed has run (so deleting it doesn't bring it back). */
interface FirstLaunchFlag {
    fun isFirst(): Boolean
    fun markDone()
}
