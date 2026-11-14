package noel.chronova.feature.timeline.presentation

import noel.chronova.core.model.TaskColor
import noel.chronova.core.ui.assistant.AssistantSearchState
import noel.chronova.core.ui.assistant.AssistantViewState
import noel.chronova.core.ui.assistant.ViewColor
import noel.chronova.core.ui.assistant.ViewZoom
import noel.chronova.core.ui.assistant.ViewScale
import androidx.compose.ui.test.onFirst
import noel.chronova.core.ui.assistant.AssistantViewAction
import noel.chronova.core.model.TimelineConfig
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import noel.chronova.core.model.TimeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import noel.chronova.core.ui.R as CoreUiR

/**
 * The timeline screen and its task editor, rendered on a device (this module's own test app) from the
 * real [ProjectTimelineViewModel] over an in-memory repository. Only visible text and semantics are checked.
 */
@RunWith(AndroidJUnit4::class)
class ProjectTimelineScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val screen = TimelineScreenHarness(compose)
    private val survey get() = screen.task("survey", "Survey", screen.at(2, 10), screen.at(2, 11), 0)
    private val build get() = screen.task("build", "Build", screen.at(2, 10, 30), screen.at(2, 12), 1)
    private val paint get() = screen.task("paint", "Paint", screen.at(5, 9), screen.at(5, 10), 2)

    private fun exists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** A predecessor choice in the editor (a selectable chip, unlike the row cells with the same title). */
    private fun predecessorChip(title: String) = compose.onNode(hasText(title) and isSelectable())

    // Rows.

    @Test
    fun theAddTaskButtonHasALabelForTalkBack() {
        screen.show(listOf(survey))
        // What an accessibility service sees: the merged button node, named and clickable.
        compose.onNode(hasContentDescription(screen.string(R.string.task_add)) and hasClickAction()).assertExists()
    }

    @Test
    fun listsEveryTaskAndPointsToTheOnesOffScreen() {
        screen.show(listOf(survey, build, paint))
        // The view opens just before the first task: the first day's morning.
        screen.nameCell("Survey").assertIsDisplayed()
        compose.onNodeWithText(screen.range(survey.startMillis, survey.endMillis)).assertIsDisplayed()
        screen.nameCell("Build").assertIsDisplayed()
        // Three days later: its row is there (the grid pans freely both ways), its bar isn't, and an arrow points to it.
        screen.nameCell("Paint").assertIsDisplayed()
        assertTrue(bar("Paint").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText(screen.string(R.string.tasks_empty)).assertDoesNotExist()

        compose.onNode(hasContentDescription(screen.res.getString(R.string.task_show_off_screen, "Paint")) and hasClickAction()).performClick()
        compose.waitUntil(5_000) { bar("Paint").fetchSemanticsNodes().isNotEmpty() }
        bar("Paint").onFirst().assertIsDisplayed()
        // Now Survey is the one off to the left.
        compose.onNode(hasContentDescription(screen.res.getString(R.string.task_show_off_screen, "Survey"))).assertExists()
    }

    // Pushing what waits for a moved task.

    @Test
    fun draggingATaskOntoTheOneThatWaitsForItOffersToPushIt() {
        val plaster = screen.task("plaster", "Plaster", screen.at(2, 11), screen.at(2, 12), 1)
        screen.show(
            listOf(survey, plaster),
            listOf(screen.link("survey", "plaster")),
            config = TimelineConfig(TimeScale.HOURS, unitWidthDp = 96f, snapMinutes = 15),
        )
        // Drag Survey (10–11) half an hour later: it now ends after Plaster starts.
        val bar = compose.onNode(hasText("Survey") and !hasClickAction() and !hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
        val bounds = bar.getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput {
            val start = Offset(((bounds.left + bounds.right) / 2 - root.left).toPx(), ((bounds.top + bounds.bottom) / 2 - root.top).toPx())
            val dx = 48.dp.toPx() // 30 minutes at 96 dp an hour
            down(start)
            repeat(25) { moveBy(Offset(dx / 25, 0f)) }
            up()
        }
        val offer = screen.res.getQuantityString(R.plurals.push_offer, 1, "Survey", 1)
        compose.waitUntil(5_000) { exists(offer) }
        compose.onNodeWithText(screen.string(R.string.push_action)).performClick()
        compose.waitUntil(5_000) { screen.repository.tasks.value.single { it.id == "plaster" }.startMillis == screen.at(2, 11, 30) }
        // Plaster now starts when Survey ends, keeping its hour, and Undo is offered.
        assertEquals(screen.at(2, 12, 30), screen.repository.tasks.value.single { it.id == "plaster" }.endMillis)
        compose.waitUntil(5_000) { exists(screen.res.getQuantityString(R.plurals.push_done, 1, 1)) }
    }

    @Test
    fun theCriticalPathSwitchMarksTheTasksThatSetTheFinishAndIsRemembered() {
        val plaster = screen.task("plaster", "Plaster", screen.at(2, 11), screen.at(2, 12), 1)
        val sweep = screen.task("sweep", "Sweep", screen.at(2, 10), screen.at(2, 10, 30), 2)
        screen.show(listOf(survey, plaster, sweep), listOf(screen.link("survey", "plaster")))
        val critical = screen.string(R.string.task_bar_state_critical)
        compose.onAllNodes(hasStateDescription(critical)).assertCountEquals(0)

        compose.onNode(hasContentDescription("Show critical path")).performScrollTo().performClick()
        compose.waitForIdle()
        // Survey → Plaster set the finish; Sweep can slip an hour and a half.
        compose.onNode(hasText("Survey") and hasStateDescription(critical)).assertExists()
        compose.onNode(hasText("Plaster") and hasStateDescription(critical)).assertExists()
        compose.onNode(hasText("Sweep") and hasStateDescription(critical)).assertDoesNotExist()
        assertTrue(screen.repository.viewSettings.getValue(TimelineScreenHarness.PROJECT_ID).showCriticalPath)

        // The editor says how much Sweep can slip: it is a Monday morning, so working time.
        screen.nameCell("Sweep").performClick()
        compose.onNodeWithText(screen.res.getString(R.string.task_slack_can_slip_working, "1 h 30 min")).assertExists()
    }

    // What the assistant asks to be shown.

    /** A task's bar: its title outside any clickable node (the name cell is clickable). */
    private fun bar(title: String) = compose.onAllNodes(hasText(title) and !hasClickAction() and !hasAnyAncestor(hasClickAction()), useUnmergedTree = true)

    @Test
    fun theAssistantCanShowATaskAnywhereAndGoToToday() {
        val paint = screen.task("paint", "Paint", screen.at(5, 9), screen.at(5, 10), 1)
        val inspect = screen.task("inspect", "Inspect", screen.at(3, 14), screen.at(3, 15), 2)
        screen.show(listOf(survey, paint, inspect))
        // Every task has a row, but only Survey's bar (2 Nov, 10:00) is in view to begin with.
        assertTrue(exists("Survey") && exists("Paint") && exists("Inspect"))
        assertTrue(bar("Paint").fetchSemanticsNodes().isEmpty() && bar("Inspect").fetchSemanticsNodes().isEmpty())

        screen.ask(AssistantViewAction.ShowTask("paint"))
        compose.waitUntil(5_000) { screen.handledRequests.size == 1 }
        compose.waitForIdle()
        assertEquals("paint", (screen.viewModel.state.value as TimelineScreenState.Ready).timeline.selectedTaskId)
        screen.nameCell("Paint").assertIsDisplayed()
        // Its bar too (not only its row): the view scrolled to 5 Nov.
        compose.waitUntil(5_000) { bar("Paint").fetchSemanticsNodes().isNotEmpty() }
        bar("Paint").onFirst().assertIsDisplayed()

        // Today (3 Nov, 14:00) brings Inspect's time into view.
        screen.ask(AssistantViewAction.GoToToday)
        compose.waitUntil(5_000) { screen.handledRequests.size == 2 }
        compose.waitUntil(5_000) { bar("Inspect").fetchSemanticsNodes().isNotEmpty() }
        screen.nameCell("Inspect").assertIsDisplayed()
    }

    @Test
    fun theAssistantCanChangeTheViewAsTheSettingsRowWould() {
        screen.show(listOf(survey))
        fun config() = (screen.viewModel.state.value as TimelineScreenState.Ready).timeline.config
        fun asked(action: AssistantViewAction, count: Int) {
            screen.ask(action)
            compose.waitUntil(5_000) { screen.handledRequests.size == count }
            compose.waitForIdle()
        }
        asked(AssistantViewAction.SetScale(ViewScale.DAYS), 1)
        assertEquals(TimeScale.DAYS, config().timeScale)
        asked(AssistantViewAction.Zoom(ViewZoom.IN), 2)
        assertEquals(125, config().zoomPercent)
        asked(AssistantViewAction.Zoom(ViewZoom.RESET), 3)
        assertEquals(100, config().zoomPercent)
        asked(AssistantViewAction.RowHeight(bigger = true), 4)
        assertEquals(64f, config().rowHeightDp)
        asked(AssistantViewAction.CriticalPath(on = true), 5)
        assertTrue(config().showCriticalPath)
        // Remembered for the project, as the settings row does.
        assertEquals(config(), screen.repository.viewSettings[TimelineScreenHarness.PROJECT_ID])
        compose.onNode(hasText("Survey") and hasStateDescription(screen.string(R.string.task_bar_state_critical))).assertExists()
    }

    @Test
    fun theAssistantCanSearchClearTheSearchAndOpenATask() {
        val plaster = screen.task("plaster", "Plaster", screen.at(2, 11), screen.at(2, 12), 1)
        screen.show(listOf(survey, plaster))
        fun timeline() = (screen.viewModel.state.value as TimelineScreenState.Ready).timeline
        fun asked(action: AssistantViewAction, count: Int) {
            screen.ask(action)
            compose.waitUntil(5_000) { screen.handledRequests.size == count }
            compose.waitForIdle()
        }
        // Survey is blue, Plaster green: the green chip lists only Plaster, with the bar open.
        asked(AssistantViewAction.Search(null, setOf(ViewColor.GREEN), emptySet()), 1)
        assertEquals(listOf("Plaster"), timeline().tasks.map { it.title })
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        asked(AssistantViewAction.ClearSearch, 2)
        assertEquals(listOf("Survey", "Plaster"), timeline().tasks.map { it.title })
        asked(AssistantViewAction.OpenTask("plaster"), 3)
        compose.onNodeWithText(screen.string(R.string.task_edit)).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Plaster")).assertIsDisplayed()
    }

    @Test
    fun theScreenTellsTheAssistantWhatItShows() {
        screen.show(listOf(survey), config = TimelineConfig().withTimeScale(TimeScale.DAYS))
        compose.waitUntil(5_000) { screen.reportedViews.isNotEmpty() }
        assertEquals(AssistantViewState(ViewScale.DAYS, criticalPath = false, selectedTaskId = null), screen.reportedViews.last())
        screen.viewModel.selectTask("survey")
        screen.viewModel.setShowCriticalPath(true)
        screen.viewModel.searchFor("sur", setOf(TaskColor.BLUE), emptySet())
        compose.waitForIdle()
        assertEquals(
            AssistantViewState(ViewScale.DAYS, criticalPath = true, selectedTaskId = "survey", search = AssistantSearchState("sur", setOf(ViewColor.BLUE), emptySet())),
            screen.reportedViews.last(),
        )
    }

    // Search and Today.

    @Test
    fun aSearchListsAMatchThatIsOffScreenAndBringsItIntoView() {
        screen.show(listOf(survey, build, paint))
        assertTrue(bar("Paint").fetchSemanticsNodes().isEmpty()) // three days later
        compose.onNode(hasContentDescription(screen.string(R.string.search_open)) and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("pai")
        compose.waitForIdle()
        screen.nameCell("Paint").assertIsDisplayed()
        compose.onAllNodesWithText("Survey").fetchSemanticsNodes().let { assertEquals(0, it.size) }
        compose.onNodeWithText(screen.res.getQuantityString(R.plurals.search_count, 3, 1, 3)).assertIsDisplayed()

        // Closing the search lists every task again.
        // The top bar's search button closes it too (the field's own close button can sit under the keyboard).
        compose.onAllNodes(hasContentDescription(screen.string(R.string.search_close)) and hasClickAction())[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText(screen.string(R.string.search_hint)).assertDoesNotExist()
    }

    @Test
    fun aSearchWithNoMatchSaysSo() {
        screen.show(listOf(survey, build))
        compose.onNode(hasContentDescription(screen.string(R.string.search_open)) and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("roof")
        compose.onNodeWithText(screen.string(R.string.tasks_no_match)).assertIsDisplayed()
    }

    @Test
    fun todayScrollsToNow() {
        val inspect = screen.task("inspect", "Inspect", screen.at(3, 14), screen.at(3, 15), 3)
        screen.show(listOf(survey, inspect))
        assertTrue(bar("Inspect").fetchSemanticsNodes().isEmpty()) // the next afternoon
        compose.onNode(hasContentDescription(screen.string(R.string.today_go)) and hasClickAction()).performClick()
        compose.waitUntil(5_000) { bar("Inspect").fetchSemanticsNodes().isNotEmpty() }
        bar("Inspect").onFirst().assertIsDisplayed()
        assertTrue(bar("Survey").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun anEmptyProjectSaysHowToAddATask() {
        screen.show(emptyList())
        compose.onNodeWithText(screen.string(R.string.tasks_empty)).assertIsDisplayed()
    }

    // Scale.

    @Test
    fun switchingTheScaleChangesTheSnapChoicesAndIsRemembered() {
        screen.show(listOf(survey, build))
        val hours = screen.button("Hours")
        val days = screen.button("Days")
        hours.assertIsSelected()
        days.assertIsNotSelected()
        // Hours snap to quarter hours; days don't offer that.
        assertTrue(exists("15m"))
        assertTrue(!exists("6h"))

        days.performClick()
        days.assertIsSelected()
        hours.assertIsNotSelected()
        assertTrue(!exists("15m"))
        assertTrue(exists("6h"))
        assertTrue(exists("1d"))
        compose.waitForIdle()
        assertEquals(TimeScale.DAYS, screen.repository.viewSettings[TimelineScreenHarness.PROJECT_ID]?.timeScale)

        // The scale only changes the view: the tasks keep their times.
        screen.nameCell("Survey").assertIsDisplayed()
        compose.onNodeWithText(screen.range(survey.startMillis, survey.endMillis)).assertIsDisplayed()
        assertEquals(listOf(survey, build), screen.repository.tasks.value)
    }

    // Editor.

    @Test
    fun addingATaskShowsItsRow() {
        screen.show(listOf(survey))
        screen.addTaskButton().performClick()
        compose.onNodeWithText(screen.string(R.string.task_new)).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText(screen.string(R.string.task_title))).performTextInput("Inspect")
        screen.button(screen.string(R.string.add)).performClick()

        // A new task starts at 09:00 today, for an hour, and its row is listed even off screen (it is selected).
        compose.waitUntil(5_000) { screen.repository.tasks.value.size == 2 }
        compose.onNodeWithText(screen.string(R.string.task_new)).assertDoesNotExist()
        screen.nameCell("Inspect").assertIsDisplayed()
        compose.onNodeWithText(screen.range(screen.at(3, 9), screen.at(3, 10))).assertIsDisplayed()
        val added = screen.repository.tasks.value.single { it.title == "Inspect" }
        assertEquals(screen.at(3, 9) to screen.at(3, 10), added.startMillis to added.endMillis)
    }

    @Test
    fun aTaskNeedsATitle() {
        screen.show(listOf(survey))
        screen.addTaskButton().performClick()
        screen.button(screen.string(R.string.add)).performClick()
        compose.onNodeWithText(screen.string(R.string.task_problem_blank_title)).assertIsDisplayed()
        assertEquals(1, screen.repository.tasks.value.size)

        // Typing a title clears it.
        compose.onNode(hasSetTextAction() and hasText(screen.string(R.string.task_title))).performTextInput("Inspect")
        compose.onNodeWithText(screen.string(R.string.task_problem_blank_title)).assertDoesNotExist()
    }

    @Test
    fun aTaskShorterThanTheMinimumIsRefused() {
        // Stored that way (e.g. by an older version); saving it again asks for at least 15 minutes.
        val short = screen.task("short", "Call", screen.at(2, 10), screen.at(2, 10, 10), 0)
        screen.show(listOf(short))
        screen.nameCell("Call").performClick()
        compose.onNodeWithText(screen.string(R.string.task_edit)).assertIsDisplayed()
        screen.button(screen.string(CoreUiR.string.save)).performClick()
        compose.onNodeWithText(screen.res.getQuantityString(R.plurals.task_problem_too_short, 15, 15)).assertIsDisplayed()
        assertEquals(listOf(short), screen.repository.tasks.value)
    }

    @Test
    fun aTaskThatEndsBeforeItStartsIsRefused() {
        val call = screen.task("call", "Call", screen.at(2, 10), screen.at(2, 11), 0)
        screen.show(listOf(call))
        screen.nameCell("Call").performClick()
        // The time picker reports the chosen end through this intent; its dial isn't a stable test target.
        compose.runOnIdle { screen.viewModel.onTaskEndChange(screen.at(2, 9)) }
        screen.button(screen.string(CoreUiR.string.save)).performClick()
        compose.onNodeWithText(screen.string(R.string.task_problem_end_not_after_start)).assertIsDisplayed()
        compose.onNodeWithText(screen.string(R.string.task_edit)).assertIsDisplayed() // the sheet stays open
        assertEquals(listOf(call), screen.repository.tasks.value)
    }

    @Test
    fun aTaskOutsideTheProjectIsRefused() {
        // Starts the evening before the project's first day.
        val early = screen.task("early", "Prep", screen.at(1, 23), screen.at(2, 1), 0)
        screen.show(listOf(early))
        screen.nameCell("Prep").performClick()
        screen.button(screen.string(CoreUiR.string.save)).performClick()
        compose.onNodeWithText(screen.string(R.string.task_problem_outside_project)).assertIsDisplayed()
        assertEquals(listOf(early), screen.repository.tasks.value)
    }

    @Test
    fun aTaskCannotDependOnItsOwnSuccessor() {
        // Survey must finish before Build starts.
        screen.show(listOf(survey, build, paint), listOf(screen.link("survey", "build")))
        screen.nameCell("Survey").performClick()
        // The editor's fields scroll; the dependencies are at the end.
        compose.onNodeWithText(screen.string(R.string.task_depends_on)).performScrollTo().assertIsDisplayed()
        // Build depends on Survey, so Survey can't depend on Build (a cycle); Paint is free.
        predecessorChip("Build").assertIsNotEnabled().assertIsNotSelected()
        predecessorChip("Paint").assertIsEnabled().assertIsNotSelected()
        predecessorChip("Survey").assertDoesNotExist() // not itself

        predecessorChip("Build").performClick()
        predecessorChip("Build").assertIsNotSelected()
        predecessorChip("Paint").performScrollTo().performClick()
        predecessorChip("Paint").assertIsSelected()
        screen.button(screen.string(CoreUiR.string.save)).performClick()
        compose.waitUntil(5_000) { screen.repository.dependencies.value.size == 2 }
        assertEquals(
            setOf("survey" to "build", "paint" to "survey"),
            screen.repository.dependencies.value.map { it.predecessorId to it.successorId }.toSet(),
        )
    }

    @Test
    fun aSuccessorShowsItsPredecessorChosen() {
        screen.show(listOf(survey, build), listOf(screen.link("survey", "build")))
        screen.nameCell("Build").performClick()
        predecessorChip("Survey").assertIsEnabled().assertIsSelected()
    }

    @Test
    fun deletingATaskCanBeUndone() {
        screen.show(listOf(survey, build))
        screen.nameCell("Survey").performClick()
        screen.button(screen.string(CoreUiR.string.delete)).performClick()

        compose.waitUntil(5_000) { screen.repository.tasks.value.none { it.id == "survey" } }
        compose.onAllNodesWithText("Survey").fetchSemanticsNodes().let { assertEquals(0, it.size) }
        compose.onNodeWithText(screen.string(R.string.task_deleted)).assertIsDisplayed()

        compose.onNode(hasText(screen.string(CoreUiR.string.undo)) and hasClickAction()).performClick()
        compose.waitUntil(5_000) { screen.repository.tasks.value.any { it.id == "survey" } }
        screen.nameCell("Survey").assertIsDisplayed()
        compose.onNodeWithText(screen.range(survey.startMillis, survey.endMillis)).assertIsDisplayed()
        assertEquals(survey, screen.repository.tasks.value.single { it.id == "survey" })
    }
}
