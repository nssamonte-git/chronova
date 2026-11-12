package noel.chronova.feature.timeline.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TimeScale
import noel.chronova.core.model.TimelineConfig
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.feature.timeline.domain.TimelineUseCases

/**
 * One project's timeline. Tasks come from the repository (the source of truth);
 * view configuration and selection are screen state. Gestures commit a finished
 * edit once, on drag end, and the repository flow brings it back.
 */
class ProjectTimelineViewModel(
    private val projectId: String,
    private val useCases: TimelineUseCases,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {

    private val _notice = MutableStateFlow<TimelineNotice?>(null)

    /** A one-off message for the screen, cleared by [dismissNotice]. */
    val notice: StateFlow<TimelineNotice?> = _notice.asStateFlow()

    /** Null until this project's saved view settings have been read. */
    private val config = MutableStateFlow<TimelineConfig?>(null)
    private val selectedTaskId = MutableStateFlow<String?>(null)

    private val _taskEditor = MutableStateFlow<TaskEditorState?>(null)

    /** The add/edit task sheet, or null when closed. */
    val taskEditor: StateFlow<TaskEditorState?> = _taskEditor.asStateFlow()

    private val _undoOffer = MutableStateFlow<UndoOffer?>(null)

    /** The last change set that can be undone, offered until [dismissUndo]. */
    val undoOffer: StateFlow<UndoOffer?> = _undoOffer.asStateFlow()

    val state: StateFlow<TimelineScreenState> = combine(
        useCases.observeSchedule(projectId),
        config,
        selectedTaskId,
    ) { schedule, config, selected ->
        if (schedule == null) {
            TimelineScreenState.NotFound
        } else if (config == null) {
            // Waiting for saved settings, so the first frame is already at the right scale and zoom.
            TimelineScreenState.Loading
        } else {
            val (project, tasks, dependencies) = schedule
            TimelineScreenState.Ready(
                TimelineUiState(
                    projectName = project.name,
                    tasks = tasks,
                    window = project.window(zone),
                    zone = zone,
                    config = config,
                    // A selection whose task disappeared is dropped.
                    selectedTaskId = selected?.takeIf { id -> tasks.any { it.id == id } },
                    dependencies = dependencies,
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimelineScreenState.Loading)

    /** Selects a task (shows its resize handles); null clears the selection. */
    fun selectTask(taskId: String?) {
        selectedTaskId.value = taskId
    }

    /**
     * Saves a dragged or resized task through the shared apply path. [original] is
     * the task as the drag started from; if it changed in the meantime (e.g. a
     * sync), the edit is refused and the user told. Tasks that aren't in this
     * project are ignored.
     */
    fun commitEdit(original: SchedulerTask, updated: SchedulerTask) {
        val ready = state.value as? TimelineScreenState.Ready ?: return
        if (ready.timeline.tasks.none { it.id == updated.id }) return
        if (updated.projectId != projectId || original.id != updated.id || original.projectId != projectId) return
        if (original == updated) return
        viewModelScope.launch {
            val result = useCases.commitEdit(projectId, original, updated)
            if (result is ApplyResult.Rejected) _notice.value = TimelineNotice.EditNotSaved
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    // Task editor intents.

    /**
     * Opens the editor for a new one-hour task at 09:00 on today's date when today
     * is inside the project, otherwise on the project's first day.
     */
    fun startAddTask() {
        val window = readyTimeline()?.window ?: return
        val firstDay = Instant.ofEpochMilli(window.startMillis).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(window.endMillis - 1).atZone(zone).toLocalDate()
        val todayDate = today()
        val day = if (todayDate in firstDay..lastDay) todayDate else firstDay
        val start = day.atTime(DEFAULT_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        _taskEditor.value = TaskEditorState(
            taskId = null,
            title = "",
            startMillis = start,
            endMillis = start + DEFAULT_DURATION_MILLIS,
            color = TaskColor.entries[(readyTimeline()?.tasks?.size ?: 0) % TaskColor.entries.size],
        )
    }

    fun startEditTask(taskId: String) {
        val timeline = readyTimeline() ?: return
        val task = timeline.tasks.firstOrNull { it.id == taskId } ?: return
        _taskEditor.value = TaskEditorState(
            taskId = task.id,
            title = task.title,
            startMillis = task.startMillis,
            endMillis = task.endMillis,
            color = task.color,
            predecessorIds = timeline.dependencies.filter { it.successorId == taskId }.map { it.predecessorId }.toSet(),
            forbiddenPredecessorIds = useCases.forbiddenPredecessors(taskId, timeline.dependencies),
        )
    }

    /** Adds or removes a predecessor in the editor; forbidden ones are ignored. */
    fun onTogglePredecessor(predecessorId: String) = _taskEditor.update { editor ->
        when {
            editor == null || predecessorId in editor.forbiddenPredecessorIds -> editor
            predecessorId in editor.predecessorIds -> editor.copy(predecessorIds = editor.predecessorIds - predecessorId)
            else -> editor.copy(predecessorIds = editor.predecessorIds + predecessorId)
        }
    }

    fun onTaskTitleChange(title: String) = _taskEditor.update { it?.copy(title = title, problem = null) }

    /** Moving the start keeps the duration, so the end moves with it. */
    fun onTaskStartChange(startMillis: Long) = _taskEditor.update {
        it?.copy(startMillis = startMillis, endMillis = startMillis + (it.endMillis - it.startMillis), problem = null)
    }

    fun onTaskEndChange(endMillis: Long) = _taskEditor.update { it?.copy(endMillis = endMillis, problem = null) }

    fun onTaskColorChange(color: TaskColor) = _taskEditor.update { it?.copy(color = color) }

    fun dismissTaskEditor() {
        _taskEditor.value = null
    }

    fun saveTask() {
        val entered = _taskEditor.value?.takeUnless { it.saving } ?: return
        val timeline = readyTimeline() ?: return
        val problem = useCases.validateTask(entered.title, entered.startMillis, entered.endMillis, timeline.window)
        if (problem != null) {
            _taskEditor.value = entered.copy(problem = problem)
            return
        }
        _taskEditor.value = entered.copy(saving = true)
        viewModelScope.launch {
            val taskId = useCases.saveTask(
                projectId = projectId,
                taskId = entered.taskId,
                existing = entered.taskId?.let { id -> timeline.tasks.firstOrNull { it.id == id } },
                title = entered.title,
                startMillis = entered.startMillis,
                endMillis = entered.endMillis,
                color = entered.color,
                predecessorIds = entered.predecessorIds,
            )
            if (entered.taskId == null) selectedTaskId.value = taskId
            _taskEditor.value = null
        }
    }

    /** Deletes the task being edited, with its links, and offers undo. */
    fun deleteEditingTask() {
        val taskId = _taskEditor.value?.taskId ?: return
        val timeline = readyTimeline() ?: return
        val task = timeline.tasks.firstOrNull { it.id == taskId } ?: return
        _taskEditor.value = null
        // Saved through the shared apply path; a refusal (the schedule changed meanwhile) is reported.
        viewModelScope.launch {
            when (val result = useCases.deleteTask(projectId, task, timeline.dependencies)) {
                is ApplyResult.Applied -> _undoOffer.value = UndoOffer(UndoKind.TaskDeleted, result.undo)
                is ApplyResult.Rejected -> _notice.value = TimelineNotice.EditNotSaved
            }
        }
    }

    /** Reverses the offered change set, unless those tasks were edited again since. */
    fun undo() {
        val offer = _undoOffer.value ?: return
        _undoOffer.value = null
        viewModelScope.launch {
            if (useCases.undo(projectId, offer.changes) is ApplyResult.Rejected) _notice.value = TimelineNotice.UndoNotPossible
        }
    }

    fun dismissUndo() {
        _undoOffer.value = null
    }

    private fun readyTimeline(): TimelineUiState? = (state.value as? TimelineScreenState.Ready)?.timeline

    // Configuration intents. They change how time is displayed and snapped and
    // never touch task timestamps.

    fun setTimeScale(scale: TimeScale) = updateConfig { it.withTimeScale(scale) }

    fun zoomIn() = updateConfig { it.zoomedIn() }

    fun zoomOut() = updateConfig { it.zoomedOut() }

    fun resetZoom() = updateConfig { it.zoomReset() }

    fun setSnapMinutes(minutes: Int) = updateConfig { it.withSnap(minutes) }

    fun changeRowHeight(steps: Int) = updateConfig { it.withRowHeightStep(steps) }

    /** Applies a view change and remembers it for this project on this device. */
    fun updateConfig(transform: (TimelineConfig) -> TimelineConfig) {
        val current = config.value ?: return
        val next = transform(current)
        if (next == current) return
        config.value = next
        viewModelScope.launch { useCases.saveViewSettings(projectId, next) }
    }

    /** A pinch zoomed without saving yet (see [finishZoom]). */
    private var zoomUnsaved = false

    /** One pinch step: zooms at once, without saving every step of the gesture. */
    fun zoomBy(factor: Float) {
        val current = config.value ?: return
        val next = current.zoomedBy(factor)
        if (next == current) return
        config.value = next
        zoomUnsaved = true
    }

    /** The pinch ended: remember the zoom it settled on, once. */
    fun finishZoom() {
        if (!zoomUnsaved) return
        zoomUnsaved = false
        val settled = config.value ?: return
        viewModelScope.launch { useCases.saveViewSettings(projectId, settled) }
    }

    init {
        viewModelScope.launch { config.value = useCases.loadViewSettings(projectId) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val DEFAULT_START_HOUR = 9
        private const val DEFAULT_DURATION_MILLIS = 60 * 60_000L

        /** [projectId] comes from the timeline's navigation argument (the shell reads it). */
        fun factory(projectId: String, useCases: () -> TimelineUseCases): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProjectTimelineViewModel(projectId, useCases()) }
        }
    }
}
