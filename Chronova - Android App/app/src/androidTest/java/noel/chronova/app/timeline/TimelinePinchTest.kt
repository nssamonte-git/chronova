package noel.chronova.app.timeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneOffset
import noel.chronova.app.presentation.components.SchedulerTimeline
import noel.chronova.app.presentation.timeline.TimelineUiState
import noel.chronova.app.ui.theme.ChronovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Two-finger pinch on the timeline grid zooms; one finger still scrolls. No app data is used. */
@RunWith(AndroidJUnit4::class)
class TimelinePinchTest {
    @get:Rule
    val compose = createComposeRule()

    private val factors = mutableListOf<Float>()
    private var ends = 0
    private val commits = mutableListOf<String>()

    @Before
    fun setUp() {
        compose.setContent {
            ChronovaTheme {
                SchedulerTimeline(
                    state = TimelineUiState.sample(ZoneOffset.UTC),
                    onSelectTask = {},
                    onCommitEdit = { _, edited -> commits += edited.title },
                    onEditTask = {},
                    onZoomBy = { factors += it },
                    onZoomEnd = { ends++ },
                    modifier = Modifier.fillMaxSize().testTag("timeline"),
                )
            }
        }
    }

    private fun pinch(spread: Boolean) = compose.onNodeWithTag("timeline").performTouchInput {
        // Fingers on the grid, below the header, moving apart (or together) horizontally.
        val mid = Offset(centerX + width / 8f, centerY + height / 8f)
        val near = Offset(60f, 0f)
        val far = Offset(240f, 0f)
        if (spread) pinch(mid - near, mid - far, mid + near, mid + far, durationMillis = 500)
        else pinch(mid - far, mid - near, mid + far, mid + near, durationMillis = 500)
    }

    private val zoom get() = factors.fold(1f) { acc, f -> acc * f }

    @Test
    fun spreadingTwoFingersZoomsIn() {
        pinch(spread = true)
        compose.waitForIdle()
        assertTrue("zoom $zoom from ${factors.size} steps", zoom > 1.5f)
        assertEquals(1, ends)
    }

    @Test
    fun pinchingTwoFingersTogetherZoomsOut() {
        pinch(spread = false)
        compose.waitForIdle()
        assertTrue("zoom $zoom from ${factors.size} steps", zoom < 0.7f)
        assertEquals(1, ends)
    }

    @Test
    fun theFingerLeftOnABarAfterAPinchDoesNotMoveIt() {
        // One finger on empty grid, one resting on a bar; they pinch; the first lifts and the one on
        // the bar then slides sideways before lifting. Nothing may be dragged or saved.
        val root = compose.onNodeWithTag("timeline").getUnclippedBoundsInRoot()
        // The title is in the name column and on the bar; the bar is the one further right.
        val bar = compose.onAllNodesWithText("UI Design").fetchSemanticsNodes().map { it.boundsInRoot }.maxBy { it.left }
        compose.onNodeWithTag("timeline").performTouchInput {
            val onBar = Offset(bar.center.x - root.left.toPx(), bar.center.y - root.top.toPx())
            val empty = Offset(onBar.x, centerY + height / 4f)
            down(0, empty)
            down(1, onBar)
            repeat(10) { step ->
                moveTo(0, empty + Offset(0f, 12f * (step + 1)))
                moveTo(1, onBar)
            }
            up(0)
            repeat(10) { step -> moveTo(1, onBar + Offset(30f * (step + 1), 0f)) }
            up(1)
        }
        compose.waitForIdle()
        assertTrue("saved after a pinch: $commits", commits.isEmpty())
    }

    @Test
    fun oneFingerScrollsWithoutZooming() {
        compose.onNodeWithTag("timeline").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue(factors.isEmpty())
        assertEquals(0, ends)
    }
}
