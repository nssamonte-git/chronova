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
)

/** A locally edited project: stamped with [now] and marked for sync. */
internal fun Project.toEntity(now: Long) = ProjectEntity(
    id = id,
    name = name,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    updatedAt = now,
)

internal fun TaskEntity.toDomain() = SchedulerTask(
    id = id,
    projectId = projectId,
    title = title,
    startMillis = startMillis,
    endMillis = endMillis,
    // Unknown names (e.g. from a newer app version via sync) fall back to the default.
    color = TaskColor.entries.firstOrNull { it.name == color } ?: TaskColor.BLUE,
    sortOrder = sortOrder,
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
)
