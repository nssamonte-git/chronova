package noel.chronova.app.presentation.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
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
import noel.chronova.app.domain.TaskRules
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskColor
import noel.chronova.app.domain.model.TimeScale
import noel.chronova.app.domain.model.TimelineConfig
import noel.chronova.app.domain.repository.ProjectRepository
import noel.chronova.app.navigation.ProjectTimelineRoute

/**
 * One project's timeline. Tasks come from the repository (the source of truth);
 * view configuration and selection are screen state. Gestures commit a finished
 * edit once, on drag end, and the repository flow brings it back.
 */
class ProjectTimelineViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {

    private val config = MutableStateFlow(TimelineConfig())
    private val selectedTaskId = MutableStateFlow<String?>(null)

    private val _taskEditor = MutableStateFlow<TaskEditorState?>(null)

    /** The add/edit task sheet, or null when closed. */
    val taskEditor: StateFlow<TaskEditorState?> = _taskEditor.asStateFlow()

    private val _deletedTask = MutableStateFlow<SchedulerTask?>(null)

    /** The task just deleted, offered for undo until [dismissDeleted]. */
    val deletedTask: StateFlow<SchedulerTask?> = _deletedTask.asStateFlow()

    val state: StateFlow<TimelineScreenState> = combine(
        repository.observeProject(projectId),
        repository.observeTasks(projectId),
        config,
        selectedTaskId,
    ) { project, tasks, config, selected ->
        if (project == null) {
            TimelineScreenState.NotFound
        } else {
            TimelineScreenState.Ready(
                TimelineUiState(
                    projectName = project.name,
                    tasks = tasks,
                    window = project.window(zone),
                    zone = zone,
                    config = config,
                    // A selection whose task disappeared is dropped.
                    selectedTaskId = selected?.takeIf { id -> tasks.any { it.id == id } },
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimelineScreenState.Loading)

    /** Selects a task (shows its resize handles); null clears the selection. */
    fun selectTask(taskId: String?) {
        selectedTaskId.value = taskId
    }

    /** Saves an edited task. Tasks that aren't in this project are ignored. */
    fun commitEdit(updated: SchedulerTask) {
        val ready = state.value as? TimelineScreenState.Ready ?: return
        if (updated.projectId != projectId || ready.timeline.tasks.none { it.id == updated.id }) return
        viewModelScope.launch { repository.updateTask(updated) }
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
        val task = readyTimeline()?.tasks?.firstOrNull { it.id == taskId } ?: return
        _taskEditor.value = TaskEditorState(task.id, task.title, task.startMillis, task.endMillis, task.color)
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
        val problem = TaskRules.validate(entered.title, entered.startMillis, entered.endMillis, timeline.window)
        if (problem != null) {
            _taskEditor.value = entered.copy(problem = problem)
            return
        }
        _taskEditor.value = entered.copy(saving = true)
        viewModelScope.launch {
            if (entered.taskId == null) {
                val id = repository.createTask(projectId, entered.title, entered.startMillis, entered.endMillis, entered.color)
                selectedTaskId.value = id
            } else {
                val existing = timeline.tasks.firstOrNull { it.id == entered.taskId }
                if (existing != null) {
                    repository.updateTask(
                        existing.copy(
                            title = entered.title,
                            startMillis = entered.startMillis,
                            endMillis = entered.endMillis,
                            color = entered.color,
                        ),
                    )
                }
            }
            _taskEditor.value = null
        }
    }

    /** Deletes the task being edited and offers undo. */
    fun deleteEditingTask() {
        val taskId = _taskEditor.value?.taskId ?: return
        val task = readyTimeline()?.tasks?.firstOrNull { it.id == taskId } ?: return
        _taskEditor.value = null
        _deletedTask.value = task
        viewModelScope.launch { repository.deleteTask(taskId) }
    }

    fun undoDelete() {
        val task = _deletedTask.value ?: return
        _deletedTask.value = null
        viewModelScope.launch { repository.updateTask(task) }
    }

    fun dismissDeleted() {
        _deletedTask.value = null
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

    fun updateConfig(transform: (TimelineConfig) -> TimelineConfig) {
        config.update(transform)
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val DEFAULT_START_HOUR = 9
        private const val DEFAULT_DURATION_MILLIS = 60 * 60_000L

        fun factory(repository: ProjectRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<ProjectTimelineRoute>()
                ProjectTimelineViewModel(route.projectId, repository)
            }
        }
    }
}
