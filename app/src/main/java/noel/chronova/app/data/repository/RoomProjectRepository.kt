package noel.chronova.app.data.repository

import androidx.room.withTransaction
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import noel.chronova.app.data.local.ChronovaDatabase
import noel.chronova.app.data.local.toDomain
import noel.chronova.app.data.local.toEntity
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.ProjectSummary
import noel.chronova.app.domain.model.SchedulerTask
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
) : ProjectRepository {

    override fun observeProjects(): Flow<List<ProjectSummary>> =
        db.projectDao().observeProjects().map { rows -> rows.map { it.toDomain() } }

    override fun observeProject(projectId: String): Flow<Project?> =
        db.projectDao().observeProject(projectId).map { it?.toDomain() }

    override fun observeTasks(projectId: String): Flow<List<SchedulerTask>> =
        db.taskDao().observeTasks(projectId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun updateTask(task: SchedulerTask) {
        db.taskDao().upsert(task.toEntity(clock()))
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
