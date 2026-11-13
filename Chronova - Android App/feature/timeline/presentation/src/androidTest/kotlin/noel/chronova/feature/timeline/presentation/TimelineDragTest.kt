package noel.chronova.feature.timeline.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import noel.chronova.core.model.TimeScale
import noel.chronova.core.model.TimelineConfig
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dragging and resizing a task bar on the real timeline screen: the result is snapped, saved once,
 * and shown in the row. Pixel distances are derived from the saved zoom (one hour = [HOUR_DP]).
 * Pinch gestures are covered by the app's TimelinePinchTest.
 */
@RunWith(AndroidJUnit4::class)
class TimelineDragTest {
    @get:Rule
    val compose = createComposeRule()

    private val screen = TimelineScreenHarness(compose)
    private val survey get() = screen.task("survey", "Survey", screen.at(2, 10), screen.at(2, 11), 0)

    @Before
    fun setUp() {
        // Hours at a known zoom, snapping to 15 minutes. The view opens an hour before the task.
        screen.show(listOf(survey), config = TimelineConfig(TimeScale.HOURS, unitWidthDp = HOUR_DP, snapMinutes = 15))
        screen.nameCell("Survey").assertIsDisplayed()
        compose.onNodeWithText(screen.range(screen.at(2, 10), screen.at(2, 11))).assertIsDisplayed()
    }

    private fun minutes(m: Int): Dp = (HOUR_DP * m / 60f).dp

    /** The bar's title (the name cell's copy is the clickable one). */
    /** The title inside the bar (the bar itself is one accessibility node, so its text is found unmerged). */
    private fun barTitle() =
        compose.onNode(hasText("Survey") and !hasClickAction() and !hasAnyAncestor(hasClickAction()), useUnmergedTree = true)

    private fun selected() =
        compose.onAllNodesWithContentDescription(screen.string(R.string.task_edit_selected)).fetchSemanticsNodes().isNotEmpty()

    private fun select() {
        barTitle().performClick()
        compose.waitUntil(5_000) { selected() }
    }

    /** One finger from [fromX] (in root coordinates, at the bar's height) [by] to the right, slowly, then lifted. */
    private fun drag(fromX: Dp, by: Dp) {
        val bar = barTitle().getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput {
            val y = ((bar.top + bar.bottom) / 2 - root.top).toPx()
            val dx = by.toPx()
            down(Offset((fromX - root.left).toPx(), y))
            repeat(STEPS) { moveBy(Offset(dx / STEPS, 0f)) }
            up()
        }
        compose.waitForIdle()
    }

    private fun barTextLeft(): Dp = barTitle().getUnclippedBoundsInRoot().left
    private fun barTextCenter(): Dp = barTitle().getUnclippedBoundsInRoot().let { (it.left + it.right) / 2 }

    private fun assertSavedAs(start: Long, end: Long) {
        compose.waitUntil(5_000) { screen.repository.updates.isNotEmpty() }
        compose.onNodeWithText(screen.range(start, end)).assertIsDisplayed()
        val saved = screen.repository.tasks.value.single()
        assertEquals(screen.range(start, end), screen.range(saved.startMillis, saved.endMillis))
        assertEquals(1, screen.repository.updates.size) // saved once, on release
        barTitle().assertIsDisplayed() // the bar shows its title again after the drag
    }

    private fun assertDpEquals(expected: Dp, actual: Dp) =
        assertEquals("expected $expected, was $actual", expected.value, actual.value, 0.6f)

    @Test
    fun draggingABarMovesItByTheSnappedAmount() {
        select()
        val before = barTextLeft()
        // 25 minutes of finger travel snaps to the nearest quarter hour: 30 minutes later.
        drag(fromX = barTextCenter(), by = minutes(25))
        assertSavedAs(screen.at(2, 10, 30), screen.at(2, 11, 30))
        assertDpEquals(minutes(30), barTextLeft() - before)
    }

    @Test
    fun draggingAnUnselectedBarMovesAndSelectsIt() {
        drag(fromX = barTextCenter(), by = -minutes(50))
        // 50 minutes earlier snaps to 45 minutes earlier.
        assertSavedAs(screen.at(2, 9, 15), screen.at(2, 10, 15))
        assertEquals(true, selected())
    }

    @Test
    fun aDragShorterThanHalfASnapStepChangesNothing() {
        select()
        val before = barTextLeft()
        drag(fromX = barTextCenter(), by = minutes(7))
        compose.waitForIdle()
        assertEquals(0, screen.repository.updates.size)
        assertEquals(survey, screen.repository.tasks.value.single())
        compose.onNodeWithText(screen.range(screen.at(2, 10), screen.at(2, 11))).assertIsDisplayed()
        assertDpEquals(0.dp, barTextLeft() - before)
    }

    @Test
    fun draggingTheRightEdgeOfASelectedBarResizesIt() {
        select()
        val before = barTextLeft()
        // The title sits a little inside the bar's left edge; the right edge is an hour further on.
        // Its handle reaches well past both sides of the edge, so this needn't be exact.
        val rightEdge = before - TITLE_INSET + minutes(60)
        drag(fromX = rightEdge - 4.dp, by = minutes(25))
        // The end moves 30 minutes (snapped); the start stays.
        assertSavedAs(screen.at(2, 10), screen.at(2, 11, 30))
        assertDpEquals(0.dp, barTextLeft() - before)
    }

    @Test
    fun draggingTheRightEdgeOfAnUnselectedBarMovesIt() {
        // Only a selected bar has resize handles.
        val rightEdge = barTextLeft() - TITLE_INSET + minutes(60)
        drag(fromX = rightEdge - 4.dp, by = minutes(25))
        assertSavedAs(screen.at(2, 10, 30), screen.at(2, 11, 30))
    }

    private companion object {
        const val HOUR_DP = 96f
        const val STEPS = 25

        /** Roughly how far the title starts inside the bar (its padding). */
        val TITLE_INSET = 10.dp
    }
}
