package noel.chronova.feature.chartimport.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.domain.notifications.NotificationRepository
import noel.chronova.core.domain.repository.ProjectRepository
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.domain.schedule.ChangeProblem
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.domain.schedule.ScheduleState

/** Prepares the image (upright, smaller, no metadata), sends it to the server and returns what it read. */
class ReadChart(
    private val api: ImportApi,
    private val images: ChartImages,
    private val accessToken: suspend () -> String?,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val today: () -> LocalDate = { LocalDate.now(zone()) },
) {
    suspend operator fun invoke(imageUri: String, target: ImportTarget): ImportedSchedule {
        val token = accessToken() ?: throw ImportApiException(ImportApiException.Kind.UNAUTHORIZED)
        val image = images.prepare(imageUri)
        val zone = zone()
        // "Z" isn't a zone name servers accept; fixed offsets stay as "+08:00".
        val zoneName = if (zone.normalized() == ZoneOffset.UTC) "UTC" else zone.id
        return api.read(token, image, zoneName, today(), (target as? ImportTarget.Existing)?.project)
    }
}

/** The open project's current schedule, which an import adds to; null when it's gone. */
class LoadImportTarget(private val repository: ProjectRepository) {
    suspend operator fun invoke(projectId: String): ScheduleState? = repository.schedule(projectId)
}

/** What applying an import did. */
sealed interface ImportOutcome {
    val projectId: String
    val taskCount: Int

    data class Created(override val projectId: String, override val taskCount: Int) : ImportOutcome

    data class Added(override val projectId: String, override val taskCount: Int, val undo: List<ScheduleChange>) : ImportOutcome

    /** Nothing written: the project changed or went meanwhile. */
    data class Failed(val problem: ChangeProblem) : ImportOutcome {
        override val projectId: String get() = ""
        override val taskCount: Int get() = 0
    }
}

/**
 * Writes a planned import in one transaction, and adds it to the notifications page ("Imported
 * from image"); adding to a project keeps the Undo there, as the assistant's changes do.
 */
class ApplyImport(
    private val repository: ProjectRepository,
    private val applyChanges: ApplyScheduleChanges,
    private val notifications: NotificationRepository?,
) {
    suspend operator fun invoke(plan: ImportPlan): ImportOutcome = when (plan) {
        is ImportPlan.NewProject -> {
            repository.createProjectWithSchedule(plan.project, plan.tasks, plan.links)
            notifications?.add(NotificationKind.IMPORTED, mapOf("count" to "${plan.taskCount}"), plan.project.id, plan.project.name)
            ImportOutcome.Created(plan.project.id, plan.taskCount)
        }
        is ImportPlan.AddToProject -> when (val result = applyChanges(plan.project.id, plan.changes)) {
            is ApplyResult.Applied -> {
                notifications?.add(NotificationKind.IMPORTED, mapOf("count" to "${plan.taskCount}"), plan.project.id, plan.project.name, result.undo)
                ImportOutcome.Added(plan.project.id, plan.taskCount, result.undo)
            }
            is ApplyResult.Rejected -> ImportOutcome.Failed(result.problem)
        }
    }
}

/** Takes an import back: deletes the project it made, or removes the tasks it added (unless edited since). */
class UndoImport(
    private val repository: ProjectRepository,
    private val applyChanges: ApplyScheduleChanges,
) {
    /** True when undone; false when the added tasks were changed since, so nothing was. */
    suspend operator fun invoke(outcome: ImportOutcome): Boolean = when (outcome) {
        is ImportOutcome.Created -> {
            repository.deleteProject(outcome.projectId)
            true
        }
        is ImportOutcome.Added -> applyChanges(outcome.projectId, outcome.undo) is ApplyResult.Applied
        is ImportOutcome.Failed -> false
    }
}

/** Everything the import sheet does. */
class ImportUseCases(
    val images: ChartImages,
    val readChart: ReadChart,
    val loadTarget: LoadImportTarget,
    val plan: PlanImport,
    val apply: ApplyImport,
    val undo: UndoImport,
)
