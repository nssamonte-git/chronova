package noel.chronova.app.presentation

import java.time.ZoneId
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TimelineConfig
import noel.chronova.app.domain.model.TimelineWindow

/** Everything the scheduler screen renders. Transient drag previews are not here. */
data class ChronovaUiState(
    val tasks: List<SchedulerTask>,
    val window: TimelineWindow,
    val zone: ZoneId,
    val config: TimelineConfig = TimelineConfig(),
    val selectedTaskId: String? = null,
) {
    companion object {
        fun sample(zone: ZoneId): ChronovaUiState = ChronovaUiState(
            tasks = SampleTasks.tasks(zone),
            window = SampleTasks.window(zone),
            zone = zone,
        )
    }
}
