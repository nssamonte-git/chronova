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
 */
object TaskEdits {
    const val MIN_DURATION_MILLIS = 15 * TimelineMath.MILLIS_PER_MINUTE

    /** Moves the task, snapping its start and preserving its duration. */
    fun move(
        original: SchedulerTask,
        deltaMillis: Long,
        snapMillis: Long,
        window: TimelineWindow,
    ): SchedulerTask {
        val duration = original.durationMillis
        val start = TimeSnapper.snap(original.startMillis + deltaMillis, snapMillis, window.startMillis)
            .coerceAtMost(window.endMillis - duration)
            .coerceAtLeast(window.startMillis)
        return original.copy(startMillis = start, endMillis = start + duration)
    }

    /** Drags the left edge: the end time is preserved and the minimum duration wins. */
    fun resizeStart(
        original: SchedulerTask,
        deltaMillis: Long,
        snapMillis: Long,
        window: TimelineWindow,
    ): SchedulerTask {
        val start = TimeSnapper.snap(original.startMillis + deltaMillis, snapMillis, window.startMillis)
            .coerceAtLeast(window.startMillis)
            .coerceAtMost(original.endMillis - MIN_DURATION_MILLIS)
        return original.copy(startMillis = start)
    }

    /** Drags the right edge: the start time is preserved and the minimum duration wins. */
    fun resizeEnd(
        original: SchedulerTask,
        deltaMillis: Long,
        snapMillis: Long,
        window: TimelineWindow,
    ): SchedulerTask {
        val end = TimeSnapper.snap(original.endMillis + deltaMillis, snapMillis, window.startMillis)
            .coerceAtMost(window.endMillis)
            .coerceAtLeast(original.startMillis + MIN_DURATION_MILLIS)
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
