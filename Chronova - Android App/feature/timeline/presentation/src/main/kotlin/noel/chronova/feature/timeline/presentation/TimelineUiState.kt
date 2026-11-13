package noel.chronova.feature.timeline.presentation

import java.time.ZoneId
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TimelineConfig
import noel.chronova.core.model.TimelineWindow

/** Everything the timeline renders for one project. Transient drag previews are not here. */
data class TimelineUiState(
    val projectName: String,
    val tasks: List<TaskUi>,
    val window: TimelineWindow,
    val zone: ZoneId,
    val config: TimelineConfig = TimelineConfig(),
    val selectedTaskId: String? = null,
    val dependencies: List<DependencyUi> = emptyList(),
) {
    companion object {
        /** The sample project, for previews. */
        fun sample(zone: ZoneId): TimelineUiState = sampleTimeline(zone)
    }
}

/** The add/edit task sheet. [taskId] is null when adding. [problem] shows after a failed save. */
data class TaskEditorState(
    val taskId: String?,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val color: TaskColor,
    /** Percent complete; a milestone is 0 or 100. */
    val progress: Int = 0,
    val notes: String = "",
    /** A moment rather than a span: [endMillis] equals [startMillis] and there is no end to pick. */
    val isMilestone: Boolean = false,
    /** Tasks this one depends on (finish-to-start). */
    val predecessorIds: Set<String> = emptySet(),
    /** Tasks that can't be picked as predecessors: itself and its successors (would form a cycle). */
    val forbiddenPredecessorIds: Set<String> = emptySet(),
    val problem: TaskProblemUi? = null,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = taskId == null

    val isDone: Boolean get() = progress == TaskUi.DONE
}

/** The timeline screen while its project loads, once loaded, or after it disappears. */
sealed interface TimelineScreenState {
    data object Loading : TimelineScreenState
    data object NotFound : TimelineScreenState
    data class Ready(val timeline: TimelineUiState) : TimelineScreenState
}

/** One-off messages from the timeline. */
enum class TimelineNotice {
    /** A drag or resize couldn't be saved because the task changed meanwhile. */
    EditNotSaved,

    /** Undo was refused because the tasks were edited again since. */
    UndoNotPossible,
}

/** An undo on offer; [id] tells one offer from the next (the ViewModel keeps what to reverse). */
data class UndoOffer(val kind: UndoKind, val id: Long)

enum class UndoKind { TaskDeleted, MarkedDone }
