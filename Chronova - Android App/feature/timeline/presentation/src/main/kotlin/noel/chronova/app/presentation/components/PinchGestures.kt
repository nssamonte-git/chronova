package noel.chronova.app.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChanged

/**
 * A two-finger pinch. [onZoom] gets each step's factor (above 1 as the fingers spread, below 1 as
 * they close) and the x between the fingers; [onEnd] runs once when the fingers of a pinch lift.
 *
 * It listens on the Initial pass, so it sees moves before the bars and the list. One-finger
 * gestures are left alone (scrolling, bar drags and taps work as before). Once a second finger
 * lands, every move and lift is consumed until all fingers are up: nothing underneath scrolls, a
 * bar drag in progress cancels, and the finger left down as a pinch ends (one usually lifts first)
 * can neither drag a bar nor count as a tap.
 */
internal suspend fun PointerInputScope.detectPinch(onZoom: (factor: Float, focusX: Float) -> Unit, onEnd: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pinched = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                pinched = true
                val zoom = event.calculateZoom()
                if (zoom != 1f && zoom.isFinite()) onZoom(zoom, event.calculateCentroid(useCurrent = true).x)
            }
            if (pinched) event.changes.forEach { if (it.positionChanged() || it.changedToUp()) it.consume() }
        } while (event.changes.any { it.pressed })
        if (pinched) onEnd()
    }
}
