package noel.chronova.app.core.timeline

import kotlin.math.min

/**
 * Chooses the [GestureMode] for a pointer-down at [x], measured in the bar's touch
 * container: `[outset][ bar of barWidth ][outset]`.
 *
 * Only a selected bar has resize zones. Each zone is the outset outside the edge
 * plus up to [inset] inside it; on narrow bars the inner part shrinks to a third of
 * the bar so the middle third always remains for moving.
 */
object HandleHitTest {
    fun modeAt(
        x: Float,
        barWidth: Float,
        outset: Float,
        inset: Float,
        selected: Boolean,
    ): GestureMode {
        if (!selected) return GestureMode.MOVE
        val inner = min(inset, barWidth / 3f)
        return when {
            x <= outset + inner -> GestureMode.RESIZE_START
            x >= outset + barWidth - inner -> GestureMode.RESIZE_END
            else -> GestureMode.MOVE
        }
    }
}
