package noel.chronova.app.domain.schedule

import java.time.ZoneId
import noel.chronova.app.domain.repository.ProjectRepository

/**
 * The one way schedule edits are saved: dragging or resizing a bar, a voice or
 * typed command, and undo all come through here.
 *
 * Inside a single repository transaction the project's current schedule is read,
 * the changes are checked against it ([ScheduleChanges.problemWith]) and written
 * only if nothing is wrong, so a change worked out from an older copy of the
 * schedule is refused rather than overwriting newer data.
 */
class ApplyScheduleChanges(
    private val repository: ProjectRepository,
    private val zone: ZoneId,
) {
    suspend operator fun invoke(projectId: String, changes: List<ScheduleChange>): ApplyResult {
        if (changes.isEmpty()) return ApplyResult.Applied(undo = emptyList())
        val problem = repository.applyChanges(projectId, changes) { current ->
            ScheduleChanges.problemWith(current, changes, zone)
        }
        return if (problem == null) ApplyResult.Applied(ScheduleChanges.inverse(changes)) else ApplyResult.Rejected(problem)
    }
}
