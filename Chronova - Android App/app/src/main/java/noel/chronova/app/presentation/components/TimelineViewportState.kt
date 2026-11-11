package noel.chronova.app.presentation.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import noel.chronova.app.core.timeline.ViewportMath

/**
 * The timeline's single horizontal scroll position, in content pixels.
 *
 * Unlike `horizontalScroll`, nothing is ever measured at the full content width
 * (which for long projects exceeds what a layout can represent): the header, grid
 * and bars read [scrollX] in their layout or draw phase and place only what is
 * near the viewport. Header and body both feed [scrollableState], so they can't
 * drift apart.
 */
@Stable
class TimelineViewportState(initialScrollX: Float = 0f) {

    var scrollX by mutableFloatStateOf(initialScrollX)
        private set

    /** Width of the visible timeline area, in px; 0 until first laid out. */
    var viewportWidth by mutableFloatStateOf(0f)
        private set

    private var maxScrollX = Float.POSITIVE_INFINITY
    private var lastPxPerMinute = Float.NaN

    val scrollableState: ScrollableState = ScrollableState { delta ->
        val old = scrollX
        scrollX = (old + delta).coerceIn(0f, maxScrollX)
        scrollX - old
    }

    /**
     * Called after every composition with the current geometry. When the scale
     * changed, the scroll is moved so the time at the viewport centre stays put;
     * it is then clamped to the new content width. Running before layout and draw
     * of the same frame means no frame is ever shown at the stale position.
     */
    fun onGeometry(contentWidth: Float, viewportWidth: Float, pxPerMinute: Float) {
        this.viewportWidth = viewportWidth
        var target = scrollX
        if (!lastPxPerMinute.isNaN() && lastPxPerMinute != pxPerMinute && viewportWidth > 0f) {
            target = ViewportMath.rescale(scrollX, viewportWidth, lastPxPerMinute, pxPerMinute)
        }
        lastPxPerMinute = pxPerMinute
        maxScrollX = ViewportMath.maxScroll(contentWidth, viewportWidth)
        val clamped = target.coerceIn(0f, maxScrollX)
        if (clamped != scrollX) scrollX = clamped
    }

    companion object {
        /** Only the position survives recreation; geometry is re-reported on the next composition. */
        val Saver: Saver<TimelineViewportState, Float> = Saver(
            save = { it.scrollX },
            restore = { TimelineViewportState(it) },
        )
    }
}
