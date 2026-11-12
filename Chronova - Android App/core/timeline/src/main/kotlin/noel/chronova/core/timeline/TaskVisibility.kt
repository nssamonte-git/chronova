package noel.chronova.core.timeline

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
     * The [items] that overlap [visible] (one that only touches its edge doesn't), plus the
     * ones in [keep] wherever they are, in their original order. Everything while [visible] is null.
     * [id], [start] and [end] read an item's id and times, so any task type can be filtered.
     */
    fun <T> onScreen(
        items: List<T>,
        visible: TimeSpan?,
        keep: Set<String> = emptySet(),
        id: (T) -> String,
        start: (T) -> Long,
        end: (T) -> Long,
    ): List<T> {
        if (visible == null) return items
        return items.filter { id(it) in keep || (start(it) < visible.endMillis && end(it) > visible.startMillis) }
    }
}
