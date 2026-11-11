package noel.chronova.app.presentation

import androidx.lifecycle.ViewModel
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TimelineConfig

/**
 * Single source of truth for tasks, view configuration and selection.
 * The UI sends intents; gestures commit a finished edit once, on drag end.
 */
class ChronovaViewModel(zone: ZoneId = ZoneId.systemDefault()) : ViewModel() {

    private val _state = MutableStateFlow(ChronovaUiState.sample(zone))
    val state: StateFlow<ChronovaUiState> = _state.asStateFlow()

    /** Selects a task (shows its resize handles); null clears the selection. */
    fun selectTask(taskId: String?) {
        _state.update { it.copy(selectedTaskId = taskId) }
    }

    /** Replaces the task with the same id. Unknown ids are ignored. */
    fun commitEdit(updated: SchedulerTask) {
        _state.update { state ->
            if (state.tasks.none { it.id == updated.id }) return@update state
            state.copy(tasks = state.tasks.map { if (it.id == updated.id) updated else it })
        }
    }

    /** Changes how time is displayed and snapped; never touches task timestamps. */
    fun updateConfig(config: TimelineConfig) {
        _state.update { it.copy(config = config) }
    }
}
