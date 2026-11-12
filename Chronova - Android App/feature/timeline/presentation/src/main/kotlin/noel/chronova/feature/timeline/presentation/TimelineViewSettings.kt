package noel.chronova.feature.timeline.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import noel.chronova.core.model.TimeScale
import noel.chronova.core.model.TimelineConfig
import noel.chronova.feature.timeline.domain.TimelineUseCases

/**
 * How one project's timeline is shown: scale, zoom, snap and row height, remembered for the project
 * on this device. These change how time is displayed and snapped and never touch task timestamps.
 * Plain Kotlin: [ProjectTimelineViewModel] owns it and shares its [scope].
 */
internal class TimelineViewSettings(
    private val projectId: String,
    private val useCases: TimelineUseCases,
    private val scope: CoroutineScope,
) {
    private val _config = MutableStateFlow<TimelineConfig?>(null)

    /** Null until this project's saved view settings have been read. */
    val config: StateFlow<TimelineConfig?> = _config.asStateFlow()

    init {
        scope.launch { _config.value = useCases.loadViewSettings(projectId) }
    }

    fun setTimeScale(scale: TimeScale) = update { it.withTimeScale(scale) }

    fun zoomIn() = update { it.zoomedIn() }

    fun zoomOut() = update { it.zoomedOut() }

    fun resetZoom() = update { it.zoomReset() }

    fun setSnapMinutes(minutes: Int) = update { it.withSnap(minutes) }

    fun changeRowHeight(steps: Int) = update { it.withRowHeightStep(steps) }

    /** Applies a view change and remembers it for this project on this device. */
    fun update(transform: (TimelineConfig) -> TimelineConfig) {
        val current = _config.value ?: return
        val next = transform(current)
        if (next == current) return
        _config.value = next
        scope.launch { useCases.saveViewSettings(projectId, next) }
    }

    /** A pinch zoomed without saving yet (see [finishZoom]). */
    private var zoomUnsaved = false

    /** One pinch step: zooms at once, without saving every step of the gesture. */
    fun zoomBy(factor: Float) {
        val current = _config.value ?: return
        val next = current.zoomedBy(factor)
        if (next == current) return
        _config.value = next
        zoomUnsaved = true
    }

    /** The pinch ended: remember the zoom it settled on, once. */
    fun finishZoom() {
        if (!zoomUnsaved) return
        zoomUnsaved = false
        val settled = _config.value ?: return
        scope.launch { useCases.saveViewSettings(projectId, settled) }
    }
}
