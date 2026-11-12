package noel.chronova.app.core.timeline

import noel.chronova.app.domain.model.SchedulerTask

/** A span of time, [startMillis] inclusive to [endMillis] exclusive. */
data class TimeSpan(val startMillis: Long, val endMillis: Long)

/**
 * Which tasks the timeline lists: only those whose time overlaps what is on screen,
 * so scrolling to another day doesn't leave rows for tasks that aren't there.
 */
object TaskVisibility {
    /** The time the timeline shows at scroll position [scrollX]; null until the viewport has a width. */
    fun visibleSpan(scrollX: Float, viewportWidth: Float, originMillis: Long, pxPerMinute: Float): TimeSpan? {
        if (viewportWidth <= 0f || pxPerMinute <= 0f) return null
        return TimeSpan(
            startMillis = originMillis + TimelineMath.pxToMillis(scrollX, pxPerMinute),
            endMillis = originMillis + TimelineMath.pxToMillis(scrollX + viewportWidth, pxPerMinute),
        )
    }

    /**
     * The [tasks] that overlap [visible] (a task that only touches its edge doesn't), plus the
     * ones in [keep] wherever they are, in their original order. Everything while [visible] is null.
     */
    fun onScreen(tasks: List<SchedulerTask>, visible: TimeSpan?, keep: Set<String> = emptySet()): List<SchedulerTask> {
        if (visible == null) return tasks
        return tasks.filter { it.id in keep || (it.startMillis < visible.endMillis && it.endMillis > visible.startMillis) }
    }
}
