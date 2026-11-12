package noel.chronova.feature.timeline.presentation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.model.TaskColor
import noel.chronova.feature.timeline.domain.ProjectSchedule
import noel.chronova.feature.timeline.domain.TimelineUseCases

/**
 * The add/edit task sheet: what it shows, its checks, and saving or deleting the task.
 * Plain Kotlin: [ProjectTimelineViewModel] owns it, shares its [scope], and handles what
 * follows (selecting a new task, offering undo, telling the user an edit was refused).
 */
internal class TaskEditor(
    private val projectId: String,
    private val useCases: TimelineUseCases,
    private val zone: ZoneId,
    private val today: () -> LocalDate,
    private val scope: CoroutineScope,
    /** The schedule as the domain has it; saves and deletes are made against it. */
    private val schedule: () -> ProjectSchedule?,
    /** What the timeline shows now, or null while it loads. */
    private val timeline: () -> TimelineUiState?,
    private val onAdded: (taskId: String) -> Unit,
    private val onDeleted: (undo: List<ScheduleChange>) -> Unit,
    private val onRefused: () -> Unit,
) {
    private val _state = MutableStateFlow<TaskEditorState?>(null)

    /** The sheet, or null when closed. */
    val state: StateFlow<TaskEditorState?> = _state.asStateFlow()

    /**
     * Opens the editor for a new one-hour task at 09:00 on today's date when today
     * is inside the project, otherwise on the project's first day.
     */
    fun startAdd() {
        val shown = timeline() ?: return
        val window = shown.window
        val firstDay = Instant.ofEpochMilli(window.startMillis).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(window.endMillis - 1).atZone(zone).toLocalDate()
        val todayDate = today()
        val day = if (todayDate in firstDay..lastDay) todayDate else firstDay
        val start = day.atTime(DEFAULT_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        _state.value = TaskEditorState(
            taskId = null,
            title = "",
            startMillis = start,
            endMillis = start + DEFAULT_DURATION_MILLIS,
            color = TaskColor.entries[shown.tasks.size % TaskColor.entries.size],
        )
    }

    fun startEdit(taskId: String) {
        val shown = timeline() ?: return
        val task = shown.tasks.firstOrNull { it.id == taskId } ?: return
        _state.value = TaskEditorState(
            taskId = task.id,
            title = task.title,
            startMillis = task.startMillis,
            endMillis = task.endMillis,
            color = task.color,
            predecessorIds = shown.dependencies.filter { it.successorId == taskId }.map { it.predecessorId }.toSet(),
            forbiddenPredecessorIds = useCases.forbiddenPredecessors(taskId, schedule()?.dependencies.orEmpty()),
        )
    }

    /** Adds or removes a predecessor; forbidden ones are ignored. */
    fun togglePredecessor(predecessorId: String) = _state.update { editor ->
        when {
            editor == null || predecessorId in editor.forbiddenPredecessorIds -> editor
            predecessorId in editor.predecessorIds -> editor.copy(predecessorIds = editor.predecessorIds - predecessorId)
            else -> editor.copy(predecessorIds = editor.predecessorIds + predecessorId)
        }
    }

    fun changeTitle(title: String) = _state.update { it?.copy(title = title, problem = null) }

    /** Moving the start keeps the duration, so the end moves with it. */
    fun changeStart(startMillis: Long) = _state.update {
        it?.copy(startMillis = startMillis, endMillis = startMillis + (it.endMillis - it.startMillis), problem = null)
    }

    fun changeEnd(endMillis: Long) = _state.update { it?.copy(endMillis = endMillis, problem = null) }

    fun changeColor(color: TaskColor) = _state.update { it?.copy(color = color) }

    fun dismiss() {
        _state.value = null
    }

    fun save() {
        val entered = _state.value?.takeUnless { it.saving } ?: return
        val shown = timeline() ?: return
        val problem = useCases.validateTask(entered.title, entered.startMillis, entered.endMillis, shown.window)
        if (problem != null) {
            _state.value = entered.copy(problem = problem.toUi())
            return
        }
        _state.value = entered.copy(saving = true)
        scope.launch {
            val taskId = useCases.saveTask(
                projectId = projectId,
                taskId = entered.taskId,
                existing = entered.taskId?.let { id -> schedule()?.tasks?.firstOrNull { it.id == id } },
                title = entered.title,
                startMillis = entered.startMillis,
                endMillis = entered.endMillis,
                color = entered.color,
                predecessorIds = entered.predecessorIds,
            )
            if (entered.taskId == null) onAdded(taskId)
            _state.value = null
        }
    }

    /** Deletes the task being edited, with its links; its undo goes to [onDeleted]. */
    fun delete() {
        val taskId = _state.value?.taskId ?: return
        val current = schedule() ?: return
        val task = current.tasks.firstOrNull { it.id == taskId } ?: return
        _state.value = null
        // Saved through the shared apply path; a refusal (the schedule changed meanwhile) is reported.
        scope.launch {
            when (val result = useCases.deleteTask(projectId, task, current.dependencies)) {
                is ApplyResult.Applied -> onDeleted(result.undo)
                is ApplyResult.Rejected -> onRefused()
            }
        }
    }

    private companion object {
        const val DEFAULT_START_HOUR = 9
        const val DEFAULT_DURATION_MILLIS = 60 * 60_000L
    }
}
