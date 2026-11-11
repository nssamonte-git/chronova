package noel.chronova.app.presentation.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import noel.chronova.app.domain.model.SchedulerTask
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
) : ViewModel() {

    private val config = MutableStateFlow(TimelineConfig())
    private val selectedTaskId = MutableStateFlow<String?>(null)

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

        fun factory(repository: ProjectRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<ProjectTimelineRoute>()
                ProjectTimelineViewModel(route.projectId, repository)
            }
        }
    }
}
