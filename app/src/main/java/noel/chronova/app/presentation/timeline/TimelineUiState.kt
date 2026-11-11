package noel.chronova.app.presentation.timeline

import java.time.ZoneId
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TimelineConfig
import noel.chronova.app.domain.model.TimelineWindow

/** Everything the timeline renders for one project. Transient drag previews are not here. */
data class TimelineUiState(
    val projectName: String,
    val tasks: List<SchedulerTask>,
    val window: TimelineWindow,
    val zone: ZoneId,
    val config: TimelineConfig = TimelineConfig(),
    val selectedTaskId: String? = null,
) {
    companion object {
        /** The sample project, for previews. */
        fun sample(zone: ZoneId): TimelineUiState = TimelineUiState(
            projectName = SampleTasks.project.name,
            tasks = SampleTasks.tasks(zone),
            window = SampleTasks.project.window(zone),
            zone = zone,
        )
    }
}

/** The timeline screen while its project loads, once loaded, or after it disappears. */
sealed interface TimelineScreenState {
    data object Loading : TimelineScreenState
    data object NotFound : TimelineScreenState
    data class Ready(val timeline: TimelineUiState) : TimelineScreenState
}
