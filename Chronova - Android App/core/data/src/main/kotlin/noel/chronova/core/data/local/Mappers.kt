package noel.chronova.core.data.local

import java.time.LocalDate
import noel.chronova.core.model.Project
import noel.chronova.core.model.ProjectSummary
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.database.DependencyEntity
import noel.chronova.core.database.ProjectEntity
import noel.chronova.core.database.ProjectWithTaskCount
import noel.chronova.core.database.TaskOutlineRow
import noel.chronova.core.model.TaskOutline
import noel.chronova.core.database.TaskEntity

internal fun ProjectEntity.toDomain() = Project(
    id = id,
    name = name,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
)

internal fun ProjectWithTaskCount.toDomain() = ProjectSummary(
    project = Project(id, name, LocalDate.parse(startDate), LocalDate.parse(endDate)),
    taskCount = taskCount,
    doneCount = doneCount,
)

/** A locally edited project: stamped with [now] and marked for sync. */
internal fun Project.toEntity(now: Long) = ProjectEntity(
    id = id,
    name = name,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    updatedAt = now,
)

/**
 * The stored task, or null when the row breaks the model's rules (only possible
 * through a bad sync record): such a row is left out rather than failing every
 * read of its project.
 */
internal fun TaskEntity.toDomain(): SchedulerTask? = runCatching {
    SchedulerTask(
        id = id,
        projectId = projectId,
        title = title,
        startMillis = startMillis,
        endMillis = endMillis,
        // Unknown names (e.g. from a newer app version via sync) fall back to the default.
        color = TaskColor.entries.firstOrNull { it.name == color } ?: TaskColor.BLUE,
        sortOrder = sortOrder,
        progress = progress,
        notes = notes,
        isMilestone = isMilestone,
    )
}.getOrNull()

/** A task's place and colour. An unknown colour falls back to the default, as in [TaskEntity.toDomain]. */
internal fun TaskOutlineRow.toDomain() = TaskOutline(
    projectId = projectId,
    startMillis = startMillis,
    endMillis = endMillis,
    color = TaskColor.entries.firstOrNull { it.name == color } ?: TaskColor.BLUE,
    isMilestone = isMilestone,
)

internal fun DependencyEntity.toDomain() = TaskDependency(id, projectId, predecessorId, successorId)

/** A locally edited link: stamped with [now] and marked for sync. */
internal fun TaskDependency.toEntity(now: Long) = DependencyEntity(id, projectId, predecessorId, successorId, updatedAt = now)

/** A locally edited task: stamped with [now] and marked for sync. */
internal fun SchedulerTask.toEntity(now: Long) = TaskEntity(
    id = id,
    projectId = projectId,
    title = title,
    startMillis = startMillis,
    endMillis = endMillis,
    color = color.name,
    sortOrder = sortOrder,
    updatedAt = now,
    progress = progress,
    notes = notes,
    isMilestone = isMilestone,
)
