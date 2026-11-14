package noel.chronova.feature.chartimport.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import noel.chronova.core.domain.DependencyRules
import noel.chronova.core.domain.ProjectProblem
import noel.chronova.core.domain.ProjectRules
import noel.chronova.core.domain.TaskProblem
import noel.chronova.core.domain.TaskRules
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.domain.schedule.ScheduleState
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency

/** What the user settled in the preview. */
data class ImportChoices(
    /** The rows to import, by ref. */
    val selected: Set<String>,
    /** Where relative days start (ignored with absolute dates). */
    val start: LocalDate,
    /** A new project's name and days (ignored when adding to a project). */
    val projectName: String = "",
    val projectStart: LocalDate = start,
    val projectEnd: LocalDate = start,
)

/** What applying will write: a whole new project, or changes to one that exists. */
sealed interface ImportPlan {
    val taskCount: Int

    data class NewProject(val project: Project, val tasks: List<SchedulerTask>, val links: List<TaskDependency>) : ImportPlan {
        override val taskCount get() = tasks.size
    }

    data class AddToProject(val project: Project, val changes: List<ScheduleChange>, override val taskCount: Int) : ImportPlan
}

sealed interface ImportProblem {
    data object NothingSelected : ImportProblem

    /** The new project's name or days can't be saved. */
    data class BadProject(val problem: ProjectProblem) : ImportProblem

    /** A ticked task doesn't fit the project. */
    data class TaskDoesNotFit(val title: String, val problem: TaskProblem) : ImportProblem
}

sealed interface PlannedImport {
    data class Ready(val plan: ImportPlan) : PlannedImport

    data class Invalid(val problem: ImportProblem) : PlannedImport
}

/**
 * Turns the preview into what will be written. Each task runs from 00:00 of its first day to
 * 24:00 of its last, or, on one day with hours written on the chart ("10–1"), for those hours; a
 * milestone stands at 09:00 of its day. New tasks take the next colours and row order after the
 * project's own; links come only between ticked tasks. Notes stay empty.
 */
class PlanImport(
    private val zone: ZoneId,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** [existing] is the project's current schedule when adding to it. */
    fun plan(schedule: ImportedSchedule, target: ImportTarget, choices: ImportChoices, existing: ScheduleState?): PlannedImport {
        val rows = ImportPreview.rows(schedule, target, choices.start).filter { it.ref in choices.selected }
        if (rows.isEmpty()) return PlannedImport.Invalid(ImportProblem.NothingSelected)

        val project = when (target) {
            is ImportTarget.Existing -> existing?.project ?: target.project
            ImportTarget.NewProject -> {
                ProjectRules.validate(choices.projectName, choices.projectStart, choices.projectEnd, emptyList(), zone)
                    ?.let { return PlannedImport.Invalid(ImportProblem.BadProject(it)) }
                Project(newId(), choices.projectName.trim(), choices.projectStart, choices.projectEnd)
            }
        }
        val window = project.window(zone)
        val already = existing?.tasks.orEmpty()
        val firstOrder = (already.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val ids = rows.associate { it.ref to newId() }

        val tasks = rows.mapIndexed { index, row ->
            val startTime = row.startTime
            val endTime = row.endTime
            val (start, end) = when {
                row.isMilestone -> millis(row.firstDay, MILESTONE_TIME).let { it to it }
                startTime != null && endTime != null -> millis(row.firstDay, startTime) to millis(row.firstDay, endTime)
                else -> millis(row.firstDay, LocalTime.MIDNIGHT) to millis(row.lastDay.plusDays(1), LocalTime.MIDNIGHT)
            }
            val task = SchedulerTask(
                id = ids.getValue(row.ref),
                projectId = project.id,
                title = row.title.trim(),
                startMillis = start,
                endMillis = end,
                color = TaskColor.entries[(already.size + index) % TaskColor.entries.size],
                sortOrder = firstOrder + index,
                progress = if (row.isMilestone && row.progress != SchedulerTask.DONE) 0 else row.progress,
                isMilestone = row.isMilestone,
            )
            TaskRules.validate(task, window)?.let { return PlannedImport.Invalid(ImportProblem.TaskDoesNotFit(task.title, it)) }
            task
        }

        val links = mutableListOf<TaskDependency>()
        for (row in rows) {
            for (ref in row.predecessorRefs) {
                val predecessor = ids[ref] ?: continue // that row wasn't ticked
                val successor = ids.getValue(row.ref)
                if (DependencyRules.canLink(predecessor, successor, links)) links += TaskDependency(newId(), project.id, predecessor, successor)
            }
        }

        val plan = when (target) {
            ImportTarget.NewProject -> ImportPlan.NewProject(project, tasks, links)
            is ImportTarget.Existing -> ImportPlan.AddToProject(
                project,
                tasks.map { ScheduleChange.CreateTask(it) } + links.map { ScheduleChange.AddLink(it) },
                tasks.size,
            )
        }
        return PlannedImport.Ready(plan)
    }

    private fun millis(day: LocalDate, time: LocalTime): Long = day.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private companion object {
        /** A day with no time, as the voice assistant reads one too. */
        val MILESTONE_TIME: LocalTime = LocalTime.of(9, 0)
    }
}
