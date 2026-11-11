package noel.chronova.app.core.timeline

import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TimelineWindow

/**
 * Pure task transformations for drag and resize.
 *
 * Every function takes the task as it was at **gesture start** plus the **total**
 * delta since then. Recomputing from the original on every pointer event (instead of
 * applying small snapped increments) avoids cumulative rounding: a slow drag of
 * less than half a snap interval per event would otherwise never move at all.
 *
 * Clamps keep the dragged edge on the snap grid too: a limit (window edge, minimum
 * duration) is rounded inward to the nearest grid line that still respects it.
 * The edge that is not being dragged is never changed, even if it is off-grid.
 */
object TaskEdits {
    const val MIN_DURATION_MILLIS = 15 * TimelineMath.MILLIS_PER_MINUTE

    /**
     * Moves the task, snapping its start and preserving its duration. Near the window
     * end the start stops at the last grid line that still fits the whole task.
     */
    fun move(
        original: SchedulerTask,
        deltaMillis: Long,
        snapMillis: Long,
        window: TimelineWindow,
    ): SchedulerTask {
        val duration = original.durationMillis
        val start = TimeSnapper.snap(original.startMillis + deltaMillis, snapMillis, window.startMillis)
            .coerceAtMost(TimeSnapper.floor(window.endMillis - duration, snapMillis, window.startMillis))
            .coerceAtLeast(window.startMillis)
        return original.copy(startMillis = start, endMillis = start + duration)
    }

    /**
     * Drags the left edge: the end time is preserved and the minimum duration wins.
     * The start stops at the last grid line that leaves at least the minimum duration.
     */
    fun resizeStart(
        original: SchedulerTask,
        deltaMillis: Long,
        snapMillis: Long,
        window: TimelineWindow,
    ): SchedulerTask {
        val start = TimeSnapper.snap(original.startMillis + deltaMillis, snapMillis, window.startMillis)
            .coerceAtLeast(window.startMillis)
            .coerceAtMost(TimeSnapper.floor(original.endMillis - MIN_DURATION_MILLIS, snapMillis, window.startMillis))
        return original.copy(startMillis = start)
    }

    /**
     * Drags the right edge: the start time is preserved and the minimum duration wins.
     * The end stops at the first grid line that leaves at least the minimum duration.
     */
    fun resizeEnd(
        original: SchedulerTask,
        deltaMillis: Long,
        snapMillis: Long,
        window: TimelineWindow,
    ): SchedulerTask {
        val end = TimeSnapper.snap(original.endMillis + deltaMillis, snapMillis, window.startMillis)
            .coerceAtMost(window.endMillis)
            .coerceAtLeast(TimeSnapper.ceil(original.startMillis + MIN_DURATION_MILLIS, snapMillis, window.startMillis))
        return original.copy(endMillis = end)
    }

    /** Dispatches on [mode] so the gesture layer has a single entry point. */
    fun apply(
        mode: GestureMode,
        original: SchedulerTask,
        deltaMillis: Long,
        snapMillis: Long,
        window: TimelineWindow,
    ): SchedulerTask = when (mode) {
        GestureMode.MOVE -> move(original, deltaMillis, snapMillis, window)
        GestureMode.RESIZE_START -> resizeStart(original, deltaMillis, snapMillis, window)
        GestureMode.RESIZE_END -> resizeEnd(original, deltaMillis, snapMillis, window)
    }
}
