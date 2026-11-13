package noel.chronova.feature.assistant.domain

import noel.chronova.core.domain.ProjectRules
import noel.chronova.core.domain.ProjectProblem
import noel.chronova.core.domain.CopyProjectResult
import noel.chronova.core.domain.CopyProject
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.domain.notifications.NotificationRepository
import noel.chronova.core.domain.repository.ProjectRepository
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.domain.schedule.ScheduleChange

// Applying, undoing and recording the assistant's changes.

/** Applies a spoken-yes change set through the one apply path; refused if the schedule changed since. */
class ApplyVoicePlan(private val applyChanges: ApplyScheduleChanges) {
    suspend operator fun invoke(projectId: String, plan: VoicePlan): ApplyResult = applyChanges(projectId, plan.changes)
}

/**
 * What undoing [undo] (the assistant's last change) would do, as a plan to ask about like any
 * other; null when there is nothing to undo or the project has gone.
 */
class PlanUndo(private val repository: ProjectRepository) {
    suspend operator fun invoke(projectId: String, undo: List<ScheduleChange>): VoicePlan? {
        if (undo.isEmpty()) return null
        val state = repository.schedule(projectId) ?: return null
        return VoicePlan(PreviewItem.from(undo, state), undo, ScheduleWarning.from(undo, state))
    }
}

/** What making a new project by voice came to. */
sealed interface NewProjectResult {
    data class Created(val projectId: String) : NewProjectResult
    data class Invalid(val problem: ProjectProblem) : NewProjectResult

    /** The project to copy has gone meanwhile. */
    data object SourceGone : NewProjectResult
}

/**
 * Makes the project [plan] describes, named [name]: empty, or a copy through the same
 * [CopyProject] as "Duplicate", after the same checks as the new-project sheet.
 */
class CreateVoiceProject(
    private val repository: ProjectRepository,
    private val copyProject: CopyProject,
    private val zone: ZoneId,
) {
    suspend operator fun invoke(plan: ProjectPlan, name: String): NewProjectResult = when (plan) {
        is ProjectPlan.Create -> {
            ProjectRules.validate(name, plan.startDate, plan.endDate, emptyList(), zone)?.let { NewProjectResult.Invalid(it) }
                ?: NewProjectResult.Created(repository.createProject(name.trim(), plan.startDate, plan.endDate))
        }
        is ProjectPlan.Copy -> when (val copied = copyProject(plan.sourceId, name, plan.startDate)) {
            is CopyProjectResult.Created -> NewProjectResult.Created(copied.projectId)
            is CopyProjectResult.Invalid -> NewProjectResult.Invalid(copied.problem)
            CopyProjectResult.SourceGone -> NewProjectResult.SourceGone
        }
    }
}

/** Undoes a project made by voice: deletes it (synced like any deletion). */
class DeleteVoiceProject(private val repository: ProjectRepository) {
    suspend operator fun invoke(projectId: String) = repository.deleteProject(projectId)
}

/** Reverses an applied change set, unless those tasks were edited again since. */
class UndoVoiceChange(private val applyChanges: ApplyScheduleChanges) {
    suspend operator fun invoke(projectId: String, undo: List<ScheduleChange>): ApplyResult = applyChanges(projectId, undo)
}

/** Adds an applied change to the notifications page, with what undoes it. */
class RecordVoiceChange(
    private val notifications: NotificationRepository?,
    private val repository: ProjectRepository,
    private val zone: ZoneId,
) {
    suspend operator fun invoke(projectId: String, plan: VoicePlan, undo: List<ScheduleChange>) {
        val notifications = notifications ?: return
        val project = repository.schedule(projectId)?.project
        val titles = ExplainedChange.from(plan.items, zone).mapNotNull { it.task ?: it.successor }.distinct()
        val args = mutableMapOf("count" to "${plan.changes.taskCount()}", "tasks" to titles.joinToString("\n"))
        val clash = plan.warnings.firstOrNull()
        when (clash) {
            is ScheduleWarning.Overlap -> args += mapOf("clash" to "overlap", "first" to clash.task, "second" to clash.other)
            is ScheduleWarning.BrokenLink -> args += mapOf("clash" to "link", "first" to clash.successor, "second" to clash.predecessor)
            null -> Unit
        }
        val kind = if (clash == null) NotificationKind.ASSISTANT_APPLIED else NotificationKind.ASSISTANT_CLASH
        notifications.add(kind, args, projectId, project?.name, undo)
    }
}

/** Tasks a change set changes (to highlight); deleted ones aren't there to highlight. */
fun List<ScheduleChange>.changedTaskIds(): Set<String> = mapNotNull {
    when (it) {
        is ScheduleChange.UpdateTask -> it.after.id
        is ScheduleChange.CreateTask -> it.task.id
        is ScheduleChange.AddLink -> it.link.successorId
        is ScheduleChange.RemoveLink -> it.link.successorId
        is ScheduleChange.DeleteTask -> null
    }
}.toSet()

/** How many tasks a change set touches. */
fun List<ScheduleChange>.taskCount(): Int = mapNotNull {
    when (it) {
        is ScheduleChange.UpdateTask -> it.after.id
        is ScheduleChange.CreateTask -> it.task.id
        is ScheduleChange.DeleteTask -> it.task.id
        is ScheduleChange.AddLink -> it.link.successorId
        is ScheduleChange.RemoveLink -> it.link.successorId
    }
}.toSet().size
