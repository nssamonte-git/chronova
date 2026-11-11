package noel.chronova.app.core.timeline

import noel.chronova.app.core.timeline.Fixtures.at
import noel.chronova.app.core.timeline.Fixtures.task
import noel.chronova.app.core.timeline.Fixtures.window
import noel.chronova.app.domain.model.TimeScale
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskEditsTest {

    private val snap15 = FixedGrid(15 * MINUTE, window.startMillis)
    private val snap60 = FixedGrid(60 * MINUTE, window.startMillis)

    // Scenario 4 — moving later and earlier preserves duration
    @Test
    fun move_later_snapsStartAndKeepsDuration() {
        val uiDesign = task(9, 0, 11, 0)
        val moved = TaskEdits.move(uiDesign, 47 * MINUTE, snap15, window)
        assertEquals(at(9, 45), moved.startMillis)
        assertEquals(at(11, 45), moved.endMillis)
        assertEquals(uiDesign.durationMillis, moved.durationMillis)
    }

    @Test
    fun move_earlier_snapsStartAndKeepsDuration() {
        val uiDesign = task(9, 0, 11, 0)
        val moved = TaskEdits.move(uiDesign, -50 * MINUTE, snap15, window) // 08:10 -> 08:15
        assertEquals(at(8, 15), moved.startMillis)
        assertEquals(at(10, 15), moved.endMillis)
    }

    @Test
    fun move_clampsToWindow() {
        val t = task(1, 0, 3, 0)
        val moved = TaskEdits.move(t, -10 * HOUR, snap15, window)
        assertEquals(window.startMillis, moved.startMillis)
        assertEquals(t.durationMillis, moved.durationMillis)
    }

    // Scenario 5 — left resize preserves end
    @Test
    fun resizeStart_preservesEnd() {
        val implementation = task(11, 0, 14, 0)
        val resized = TaskEdits.resizeStart(implementation, -65 * MINUTE, snap15, window) // 09:55 -> 10:00
        assertEquals(at(10, 0), resized.startMillis)
        assertEquals(implementation.endMillis, resized.endMillis)
    }

    // Scenario 6 — right resize preserves start
    @Test
    fun resizeEnd_preservesStart() {
        val qa = task(13, 30, 16, 0)
        val resized = TaskEdits.resizeEnd(qa, 40 * MINUTE, snap60, window) // 16:40 -> 17:00
        assertEquals(qa.startMillis, resized.startMillis)
        assertEquals(at(17, 0), resized.endMillis)
    }

    // Scenario 7 — minimum duration
    @Test
    fun resizeEnd_cannotGoBelowMinimumDuration() {
        val review = task(16, 0, 17, 0)
        val resized = TaskEdits.resizeEnd(review, -2 * HOUR, snap15, window)
        assertEquals(review.startMillis, resized.startMillis)
        assertEquals(TaskEdits.MIN_DURATION_MILLIS, resized.durationMillis)
    }

    @Test
    fun resizeStart_cannotPassEndOrGoBelowMinimumDuration() {
        val review = task(16, 0, 17, 0)
        val resized = TaskEdits.resizeStart(review, 2 * HOUR, snap15, window)
        assertEquals(review.endMillis, resized.endMillis)
        assertEquals(TaskEdits.MIN_DURATION_MILLIS, resized.durationMillis)
    }

    @Test
    fun minimumDurationHoldsEvenWithCoarseSnap() {
        val qa = task(13, 30, 16, 0)
        // Dragged to 13:00, the end must stay >= 13:45; the first 60-min grid line
        // that allows that is 14:00. The off-grid 13:30 start is kept.
        val resized = TaskEdits.resizeEnd(qa, -3 * HOUR, snap60, window)
        assertEquals(qa.startMillis, resized.startMillis)
        assertEquals(at(14, 0), resized.endMillis)
    }

    @Test
    fun resizeStart_minimumDurationStopsOnGridWithCoarseSnap() {
        val t = task(13, 0, 14, 30)
        // Start may not pass 14:15; the last 60-min grid line before that is 14:00.
        val resized = TaskEdits.resizeStart(t, 3 * HOUR, snap60, window)
        assertEquals(at(14, 0), resized.startMillis)
        assertEquals(t.endMillis, resized.endMillis)
    }

    @Test
    fun move_clampAtWindowEndStaysOnGrid() {
        val t = task(9, 0, 11, 30) // 2 h 30: not a whole number of 60-min steps
        val moved = TaskEdits.move(t, 100 * HOUR, snap60, window)
        // Latest start that fits is 21:30 on the last day; the grid line before it is 21:00.
        assertEquals(window.endMillis - 3 * HOUR, moved.startMillis)
        assertEquals(t.durationMillis, moved.durationMillis)
        assertEquals(0L, (moved.startMillis - window.startMillis) % HOUR)
    }

    @Test
    fun move_clampAtWindowEndIsFlushWhenDurationFitsGrid() {
        val t = task(1, 0, 3, 0)
        val moved = TaskEdits.move(t, 100 * HOUR, snap15, window)
        assertEquals(window.endMillis, moved.endMillis)
    }

    @Test
    fun apply_dispatchesOnGestureMode() {
        val t = task(9, 0, 11, 0)
        assertEquals(TaskEdits.move(t, HOUR, snap15, window), TaskEdits.apply(GestureMode.MOVE, t, HOUR, snap15, window))
        assertEquals(at(10), TaskEdits.apply(GestureMode.RESIZE_START, t, HOUR, snap15, window).startMillis)
        assertEquals(at(12), TaskEdits.apply(GestureMode.RESIZE_END, t, HOUR, snap15, window).endMillis)
    }

    /**
     * Why edits are computed from the gesture-start task and the total drag:
     * 100 drag events of 1 px at 1.6 px/min are each 0.625 min, far below half a
     * 15-min snap. Snapping per event would never move the task; using the
     * accumulated distance moves it by 62.5 min -> 60 min.
     */
    @Test
    fun accumulatedDragAvoidsCumulativeRoundingLoss() {
        val ppm = TimelineMath.pxPerMinute(96f, TimeScale.HOURS)
        val original = task(9, 0, 11, 0)

        var incremental = original
        repeat(100) {
            incremental = TaskEdits.move(incremental, TimelineMath.pxToMillis(1f, ppm), snap15, window)
        }
        assertEquals("per-event snapping gets stuck", original.startMillis, incremental.startMillis)

        var totalPx = 0f
        var fromStart = original
        repeat(100) {
            totalPx += 1f
            fromStart = TaskEdits.move(original, TimelineMath.pxToMillis(totalPx, ppm), snap15, window)
        }
        assertEquals(at(10, 0), fromStart.startMillis)
    }
}
