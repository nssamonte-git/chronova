package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import noel.chronova.core.timeline.GestureMode

/**
 * The single gesture detector for a task bar. One handler decides tap vs drag and,
 * for a drag, which [GestureMode] applies — so move and resize can never fire
 * together.
 *
 * - The mode is chosen once, from the pointer-down position.
 * - Only horizontal movement past touch slop starts a drag. The change is consumed
 *   then, so the parent `horizontalScroll` does not also scroll. Vertical movement
 *   is left alone, so the parent `verticalScroll` still works over bars.
 * - [onDrag] receives the **total** distance from the pointer-down position, never
 *   an increment, so callers can recompute from gesture-start values.
 * - A release without a drag is a tap (its up event is consumed so the background
 *   "tap to deselect" handler does not also fire).
 */
internal suspend fun PointerInputScope.detectTaskGestures(
    modeAt: (Offset) -> GestureMode,
    onTap: () -> Unit,
    onDragStart: (GestureMode) -> Unit,
    onDrag: (totalDx: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val mode = modeAt(down.position)

        val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
            change.consume()
        }

        if (dragStart == null) {
            // Released before slop (a tap) or taken by another gesture (e.g. vertical scroll).
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && up.changedToUp() && !up.isConsumed) {
                up.consume()
                onTap()
            }
            return@awaitEachGesture
        }

        // Measured from the down position (not just the over-slop part), so the bar
        // stays under the finger instead of lagging by the touch slop.
        var totalDx = dragStart.position.x - down.position.x
        onDragStart(mode)
        onDrag(totalDx)
        val completed = horizontalDrag(dragStart.id) { change ->
            totalDx += change.positionChange().x
            change.consume()
            onDrag(totalDx)
        }
        if (completed) onDragEnd() else onDragCancel()
    }
}
