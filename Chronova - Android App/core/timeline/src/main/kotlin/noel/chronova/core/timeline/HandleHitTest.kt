package noel.chronova.core.timeline

import kotlin.math.min

/**
 * Chooses the [GestureMode] for a pointer-down at [x]. All values are in timeline
 * content coordinates; the bar spans [barStart]..[barEnd] and its touch area may
 * extend past both edges (that outer part counts as the nearest edge).
 *
 * Only a selected bar has resize zones. Each zone reaches up to [inset] inside its
 * edge; on narrow bars the inner part shrinks to a third of the bar so the middle
 * third always remains for moving. A milestone has no length to change, so it
 * only ever moves.
 */
object HandleHitTest {
    fun modeAt(
        x: Float,
        barStart: Float,
        barEnd: Float,
        inset: Float,
        selected: Boolean,
        isMilestone: Boolean = false,
    ): GestureMode {
        if (!selected || isMilestone) return GestureMode.MOVE
        val inner = min(inset, (barEnd - barStart) / 3f)
        return when {
            x <= barStart + inner -> GestureMode.RESIZE_START
            x >= barEnd - inner -> GestureMode.RESIZE_END
            else -> GestureMode.MOVE
        }
    }
}
