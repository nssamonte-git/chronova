package noel.chronova.app.data.repository

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import noel.chronova.app.data.local.ChronovaDatabase
import noel.chronova.app.data.local.toDomain
import noel.chronova.app.data.local.toEntity
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.ProjectSummary
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskColor
import noel.chronova.app.domain.repository.ProjectRepository

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

    override suspend fun deleteTask(taskId: String) {
        db.taskDao().markDeleted(taskId, clock())
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
