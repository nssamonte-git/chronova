package noel.chronova.feature.projects.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import noel.chronova.core.domain.CalendarDays
import noel.chronova.core.domain.ProjectProblem
import noel.chronova.core.domain.ProjectRules
import noel.chronova.core.domain.TaskRules
import noel.chronova.core.domain.repository.ProjectRepository
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency

sealed interface CopyProjectResult {
    data class Created(val projectId: String) : CopyProjectResult
    data class Invalid(val problem: ProjectProblem) : CopyProjectResult

    /** The project to copy is gone (deleted, e.g. by a sync, meanwhile). */
    data object SourceGone : CopyProjectResult
}

/**
 * Starts a new project from an existing one (any project can be a template): same
 * length, starting on [startDate], with every task and link. Tasks move by whole
 * calendar days, so they keep their local times across DST; one that DST would make
 * invalid keeps its length instead. The copy starts with nothing done (progress 0);
 * titles, notes, colours, row order and milestones are kept. Everything is written in
 * one transaction, with new ids.
 */
class CopyProject(
    private val repository: ProjectRepository,
    private val zone: ZoneId,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(sourceId: String, name: String, startDate: LocalDate): CopyProjectResult {
        val source = repository.schedule(sourceId) ?: return CopyProjectResult.SourceGone
        val days = ChronoUnit.DAYS.between(source.project.startDate, startDate)
        val endDate = source.project.endDate.plusDays(days)
        ProjectRules.validate(name, startDate, endDate, emptyList(), zone)?.let { return CopyProjectResult.Invalid(it) }

        val project = Project(newId(), name.trim(), startDate, endDate)
        val window = project.window(zone)
        val taskIds = source.tasks.associate { it.id to newId() }
        val tasks = source.tasks.map { task ->
            val moved = task.copy(
                id = taskIds.getValue(task.id),
                projectId = project.id,
                startMillis = CalendarDays.shift(task.startMillis, days, zone),
                endMillis = CalendarDays.shift(task.endMillis, days, zone),
                progress = 0,
            )
            if (TaskRules.validate(moved, window) == null) moved else moved.copy(endMillis = moved.startMillis + task.durationMillis)
        }
        val links = source.links.mapNotNull { link ->
            val predecessor = taskIds[link.predecessorId] ?: return@mapNotNull null
            val successor = taskIds[link.successorId] ?: return@mapNotNull null
            TaskDependency(newId(), project.id, predecessor, successor)
        }
        repository.createProjectWithSchedule(project, tasks, links)
        return CopyProjectResult.Created(project.id)
    }
}

/** A project offered as a starting point: its name and length, for the sheet. */
data class ProjectTemplate(val projectId: String, val name: String, val startDate: LocalDate, val endDate: LocalDate) {
    val lengthDays: Long get() = ChronoUnit.DAYS.between(startDate, endDate)
}
