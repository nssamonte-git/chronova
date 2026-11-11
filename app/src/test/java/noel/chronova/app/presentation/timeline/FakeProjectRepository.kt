package noel.chronova.app.presentation.timeline

import java.time.LocalDate
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

    override suspend fun tasksOf(projectId: String): List<SchedulerTask> =
        tasks.value.filter { it.projectId == projectId }

    private var nextId = 1

    override suspend fun createProject(name: String, startDate: LocalDate, endDate: LocalDate): String {
        val id = "new-${nextId++}"
        projects.value = projects.value + Project(id, name.trim(), startDate, endDate)
        return id
    }

    override suspend fun updateProject(project: Project) {
        projects.value = projects.value.map { if (it.id == project.id) project else it }
    }

    override suspend fun deleteProject(projectId: String) {
        projects.value = projects.value.filterNot { it.id == projectId }
        tasks.value = tasks.value.filterNot { it.projectId == projectId }
    }

    override suspend fun updateTask(task: SchedulerTask) {
        updates += task
        tasks.value = tasks.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun seedSampleIfFirstLaunch() = Unit
}
