package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import noel.chronova.core.timeline.ViewportMath

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

    /**
     * Where a zoom keeps the time fixed, in px from the visible lane's left edge: set between the
     * fingers during a pinch, null otherwise (the centre, as the zoom buttons do).
     */
    var zoomAnchor: Float? = null

    private var maxScrollX = Float.POSITIVE_INFINITY
    private var lastPxPerMinute = Float.NaN

    val scrollableState: ScrollableState = ScrollableState { delta ->
        val old = scrollX
        scrollX = (old + delta).coerceIn(0f, maxScrollX)
        scrollX - old
    }

    /**
     * Called after every composition with the current geometry. When the scale
     * changed, the scroll is moved so the time at the viewport centre (or at
     * [zoomAnchor], during a pinch) stays put; it is then clamped to the new content width. Running before layout and draw
     * of the same frame means no frame is ever shown at the stale position.
     */
    fun onGeometry(contentWidth: Float, viewportWidth: Float, pxPerMinute: Float) {
        this.viewportWidth = viewportWidth
        var target = scrollX
        if (!lastPxPerMinute.isNaN() && lastPxPerMinute != pxPerMinute && viewportWidth > 0f) {
            val anchor = zoomAnchor
            target = if (anchor != null) {
                ViewportMath.rescaleAround(scrollX, anchor.coerceIn(0f, viewportWidth), lastPxPerMinute, pxPerMinute)
            } else {
                ViewportMath.rescale(scrollX, viewportWidth, lastPxPerMinute, pxPerMinute)
            }
        }
        lastPxPerMinute = pxPerMinute
        maxScrollX = ViewportMath.maxScroll(contentWidth, viewportWidth)
        val clamped = target.coerceIn(0f, maxScrollX)
        if (clamped != scrollX) scrollX = clamped
    }

    /** Smoothly scrolls so the content x [target] is at the viewport's left edge (clamped). */
    suspend fun animateScrollTo(target: Float) {
        scrollableState.animateScrollBy(target.coerceIn(0f, maxScrollX) - scrollX)
    }

    companion object {
        /** Only the position survives recreation; geometry is re-reported on the next composition. */
        val Saver: Saver<TimelineViewportState, Float> = Saver(
            save = { it.scrollX },
            restore = { TimelineViewportState(it) },
        )
    }
}
