package noel.chronova.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class HandleHitTestTest {

    private val outset = 40f
    private val inset = 40f

    /** [x] is measured in a touch container that starts [outset] before the bar. */
    private fun mode(x: Float, barWidth: Float, selected: Boolean = true) =
        HandleHitTest.modeAt(x, barStart = outset, barEnd = outset + barWidth, inset = inset, selected = selected)

    @Test
    fun unselectedBarOnlyMoves() {
        assertEquals(GestureMode.MOVE, mode(0f, 400f, selected = false))
        assertEquals(GestureMode.MOVE, mode(470f, 400f, selected = false))
    }

    @Test
    fun wideBar_edgesResizeAndMiddleMoves() {
        // Container: [0..40) outset, [40..440) bar, [440..480] outset.
        assertEquals(GestureMode.RESIZE_START, mode(0f, 400f))   // outside the left edge
        assertEquals(GestureMode.RESIZE_START, mode(80f, 400f))  // 40 px inside the left edge
        assertEquals(GestureMode.MOVE, mode(81f, 400f))
        assertEquals(GestureMode.MOVE, mode(240f, 400f))
        assertEquals(GestureMode.MOVE, mode(399f, 400f))
        assertEquals(GestureMode.RESIZE_END, mode(400f, 400f))   // 40 px inside the right edge
        assertEquals(GestureMode.RESIZE_END, mode(480f, 400f))   // outside the right edge
    }

    @Test
    fun narrowBar_keepsMiddleThirdForMoving() {
        // 60 px bar: inner zones shrink to 20 px each, middle 20 px moves.
        assertEquals(GestureMode.RESIZE_START, mode(60f, 60f))
        assertEquals(GestureMode.MOVE, mode(70f, 60f))
        assertEquals(GestureMode.RESIZE_END, mode(80f, 60f))
    }
}
