package noel.chronova.app.presentation.timeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.ProjectSummary
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.repository.ProjectRepository

/** In-memory [ProjectRepository] for ViewModel tests. */
class FakeProjectRepository(
    projects: List<Project> = emptyList(),
    tasks: List<SchedulerTask> = emptyList(),
) : ProjectRepository {
    val projects = MutableStateFlow(projects)
    val tasks = MutableStateFlow(tasks)
    val updates = mutableListOf<SchedulerTask>()

    override fun observeProjects(): Flow<List<ProjectSummary>> =
        projects.map { list -> list.map { p -> ProjectSummary(p, tasks.value.count { it.projectId == p.id }) } }

    override fun observeProject(projectId: String): Flow<Project?> =
        projects.map { list -> list.firstOrNull { it.id == projectId } }

    override fun observeTasks(projectId: String): Flow<List<SchedulerTask>> =
        tasks.map { list -> list.filter { it.projectId == projectId }.sortedBy { it.sortOrder } }

    override suspend fun updateTask(task: SchedulerTask) {
        updates += task
        tasks.value = tasks.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun seedSampleIfFirstLaunch() = Unit
}
