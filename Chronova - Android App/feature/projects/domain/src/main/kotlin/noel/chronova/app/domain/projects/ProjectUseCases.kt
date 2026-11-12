package noel.chronova.app.domain.projects

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import noel.chronova.app.domain.ProjectProblem
import noel.chronova.app.domain.ProjectRules
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.ProjectSummary
import noel.chronova.app.domain.repository.ProjectRepository

/** Every project with its task count, for the list. */
class ObserveProjects(private val repository: ProjectRepository) {
    operator fun invoke(): Flow<List<ProjectSummary>> = repository.observeProjects()
}

sealed interface SaveProjectResult {
    data class Created(val projectId: String) : SaveProjectResult
    data object Updated : SaveProjectResult
    data class Invalid(val problem: ProjectProblem) : SaveProjectResult
}

/**
 * Creates a project ([projectId] null) or saves an edited one, after checking the
 * entered values with [ProjectRules] (including that no existing task falls outside).
 */
class SaveProject(private val repository: ProjectRepository, private val zone: ZoneId) {
    suspend operator fun invoke(projectId: String?, name: String, startDate: LocalDate, endDate: LocalDate): SaveProjectResult {
        val tasks = projectId?.let { repository.tasksOf(it) }.orEmpty()
        ProjectRules.validate(name, startDate, endDate, tasks, zone)?.let { return SaveProjectResult.Invalid(it) }
        return if (projectId == null) {
            SaveProjectResult.Created(repository.createProject(name, startDate, endDate))
        } else {
            repository.updateProject(Project(projectId, name, startDate, endDate))
            SaveProjectResult.Updated
        }
    }
}

/** Deletes a project and its tasks (tombstones until synced). */
class DeleteProject(private val repository: ProjectRepository) {
    suspend operator fun invoke(projectId: String) = repository.deleteProject(projectId)
}

/** Everything the project list does. */
class ProjectUseCases(
    val observe: ObserveProjects,
    val save: SaveProject,
    val delete: DeleteProject,
)
