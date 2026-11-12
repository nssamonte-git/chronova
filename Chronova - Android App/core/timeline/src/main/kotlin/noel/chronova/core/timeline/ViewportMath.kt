package noel.chronova.core.timeline

import kotlin.math.floor
import kotlin.math.max

/**
 * Horizontal viewport arithmetic, in pixels of timeline content (x = 0 at the
 * window start). The content can be far wider than any layout may measure, so the
 * timeline never lays it out at full width: it keeps one scroll position and only
 * places what is near the viewport.
 */
object ViewportMath {
    fun maxScroll(contentWidth: Float, viewportWidth: Float): Float = max(0f, contentWidth - viewportWidth)

    /**
     * Scroll position that keeps the time at the viewport centre in place when the
     * scale changes from [oldPxPerMinute] to [newPxPerMinute] (zoom or time scale).
     */
    fun rescale(scrollX: Float, viewportWidth: Float, oldPxPerMinute: Float, newPxPerMinute: Float): Float =
        rescaleAround(scrollX, viewportWidth / 2f, oldPxPerMinute, newPxPerMinute)

    /**
     * Scroll position that keeps the time at [anchorX] (px from the viewport's left edge,
     * e.g. between the fingers of a pinch) in place when the scale changes.
     */
    fun rescaleAround(scrollX: Float, anchorX: Float, oldPxPerMinute: Float, newPxPerMinute: Float): Float {
        val anchorMinutes = (scrollX + anchorX) / oldPxPerMinute
        return anchorMinutes * newPxPerMinute - anchorX
    }

    /**
     * The span in which bars are composed: the viewport-wide "page" containing
     * [scrollX], widened by one viewport on each side. It only changes when the
     * scroll crosses a page boundary, so scrolling doesn't recompose rows every
     * frame, and it always contains the visible span [scrollX, scrollX + viewportWidth].
     */
    fun composeSpan(scrollX: Float, viewportWidth: Float): PxSpan {
        if (viewportWidth <= 0f) return PxSpan(0f, 0f)
        val page = floor(scrollX / viewportWidth)
        return PxSpan((page - 1) * viewportWidth, (page + 3) * viewportWidth)
    }
}

data class PxSpan(val start: Float, val end: Float)
