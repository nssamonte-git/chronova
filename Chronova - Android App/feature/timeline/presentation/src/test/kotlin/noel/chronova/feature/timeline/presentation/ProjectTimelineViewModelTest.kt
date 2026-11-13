package noel.chronova.feature.timeline.presentation

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TimeScale
import noel.chronova.core.model.TimelineConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import noel.chronova.feature.timeline.domain.TimelineUseCases
import noel.chronova.core.testing.FakeProjectRepository
import noel.chronova.feature.timeline.domain.ObserveProjectSchedule
import noel.chronova.feature.timeline.domain.CommitTaskEdit
import noel.chronova.feature.timeline.domain.ValidateTask
import noel.chronova.feature.timeline.domain.ForbiddenPredecessors
import noel.chronova.feature.timeline.domain.PushDependents
import noel.chronova.feature.timeline.domain.SaveTask
import noel.chronova.feature.timeline.domain.DeleteTask
import noel.chronova.feature.timeline.domain.UndoScheduleChanges
import noel.chronova.feature.timeline.domain.LoadViewSettings
import noel.chronova.feature.timeline.domain.SaveViewSettings
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.domain.repository.ProjectRepository
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectTimelineViewModelTest {

    private val zone = ZoneOffset.UTC

    /** Outside the sample project's dates unless a test changes it. */
    private var today: LocalDate = LocalDate.of(2026, 11, 11)
    private lateinit var repository: FakeProjectRepository
    private lateinit var viewModel: ProjectTimelineViewModel

    private fun at(hour: Int, minute: Int = 0): Long =
        SampleTasks.REFERENCE_DATE.atTime(hour, minute).toInstant(zone).toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone))
        viewModel = ProjectTimelineViewModel(SampleTasks.PROJECT_ID, timelineUseCases(repository, zone), zone, today = { today })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Collects the state for the duration of the test (it is shared WhileSubscribed). */
    private fun TestScope.timeline(): () -> TimelineUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return { (viewModel.state.value as TimelineScreenState.Ready).timeline }
    }

    @Test
    fun loadsProjectTasksAtSpecifiedTimes() = runTest {
        val state = timeline()
        val tasks = state().tasks
        assertEquals(SampleTasks.project.name, state().projectName)
        assertEquals(listOf("UI Design", "Implementation", "QA Testing", "Review"), tasks.map { it.title })
        assertEquals(listOf(at(9), at(11), at(13, 30), at(16)), tasks.map { it.startMillis })
        assertEquals(listOf(at(11), at(14), at(16), at(17)), tasks.map { it.endMillis })
        assertNull(state().selectedTaskId)
    }

    @Test
    fun windowCoversTheProjectDays() = runTest {
        val window = timeline()().window
        assertEquals(at(0), window.startMillis)
        assertEquals(3 * 24 * 60 * 60_000L, window.lengthMillis)
    }

    @Test
    fun selectTask_setsAndClearsSelection() = runTest {
        val state = timeline()
        viewModel.selectTask("review")
        assertEquals("review", state().selectedTaskId)
        viewModel.selectTask(null)
        assertNull(state().selectedTaskId)
    }

    @Test
    fun selectionOfARemovedTaskIsDropped() = runTest {
        val state = timeline()
        viewModel.selectTask("review")
        repository.tasks.value = repository.tasks.value.filterNot { it.id == "review" }
        assertNull(state().selectedTaskId)
    }

    @Test
    fun commitEdit_savesThroughRepositoryAndKeepsRowOrder() = runTest {
        val state = timeline()
        val before = state().tasks
        val edited = before[1].copy(startMillis = at(10), endMillis = at(13))
        viewModel.commitEdit(before[1], edited)

        // Slack is worked out on screen, never stored.
        assertEquals(listOf(edited.copy(slackMillis = null)), repository.updates.map { it.toUi() })
        val after = state().tasks
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(edited.copy(slackMillis = after[1].slackMillis), after[1])
    }

    @Test
    fun commitEdit_refusesAndNotifiesWhenTheTaskChangedMeanwhile() = runTest {
        val state = timeline()
        val shown = state().tasks[1]
        // A sync changes the task in the repository; the screen hasn't caught up yet.
        val synced = repository.tasks.value.single { it.id == shown.id }.copy(title = "Changed by sync")
        repository.tasks.value = repository.tasks.value.map { if (it.id == shown.id) synced else it }
        val fresh = state().tasks[1]
        assertEquals(synced.toUi(fresh.slackMillis), fresh)
        // The edit was worked out from the copy the user saw before the sync.
        viewModel.commitEdit(shown, shown.copy(startMillis = at(10), endMillis = at(13)))

        assertEquals(TimelineNotice.EditNotSaved, viewModel.notice.value)
        assertEquals(synced.toUi(fresh.slackMillis), state().tasks[1])
        viewModel.dismissNotice()
        assertNull(viewModel.notice.value)
    }

    @Test
    fun commitEdit_ignoresUnknownTask() = runTest {
        val state = timeline()
        val task = state().tasks[0]
        viewModel.commitEdit(task, task.copy(id = "missing"))
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun deletedProjectReportsNotFound() = runTest {
        timeline()
        repository.projects.value = emptyList()
        assertEquals(TimelineScreenState.NotFound, viewModel.state.value)
    }

    @Test
    fun updateConfig_neverChangesTaskTimestamps() = runTest {
        val state = timeline()
        val before = state().tasks
        viewModel.setTimeScale(TimeScale.DAYS)
        viewModel.zoomIn()
        viewModel.setSnapMinutes(60)
        viewModel.changeRowHeight(+2)
        viewModel.setTimeScale(TimeScale.HOURS)
        viewModel.zoomOut()
        viewModel.resetZoom()

        assertEquals(TimelineConfig(snapMinutes = 60, rowHeightDp = 72f), state().config)
        assertEquals(before, state().tasks)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun addTask_defaultsToNineOnTheFirstDayWhenTodayIsOutsideTheProject() = runTest {
        timeline()
        viewModel.startAddTask()
        val editor = viewModel.taskEditor.value!!
        assertTrue(editor.isNew)
        assertEquals(at(9), editor.startMillis)
        assertEquals(at(10), editor.endMillis)
    }

    @Test
    fun addTask_defaultsToTodayWhenTodayIsInsideTheProject() = runTest {
        today = SampleTasks.REFERENCE_DATE.plusDays(1)
        timeline()
        viewModel.startAddTask()
        assertEquals(at(9) + 24 * 60 * 60_000L, viewModel.taskEditor.value!!.startMillis)
    }

    @Test
    fun addTask_savesInTheLastRowAndSelectsIt() = runTest {
        val state = timeline()
        viewModel.startAddTask()
        viewModel.onTaskTitleChange("  Launch ")
        viewModel.onTaskColorChange(TaskColor.GREEN)
        viewModel.saveTask()

        val added = state().tasks.last()
        assertEquals("Launch", added.title)
        assertEquals(TaskColor.GREEN, added.color)
        assertEquals(4, repository.tasks.value.single { it.id == added.id }.sortOrder) // the stored row order
        assertEquals(added.id, state().selectedTaskId)
        assertNull(viewModel.taskEditor.value)
    }

    @Test
    fun addTask_savesAMilestoneWithItsProgressAndNotes() = runTest {
        val state = timeline()
        viewModel.startAddTask()
        viewModel.onTaskTitleChange("Launch")
        viewModel.onTaskMilestoneChange(true)
        viewModel.onTaskDoneChange(true)
        viewModel.onTaskNotesChange("Press release at noon")
        val editor = viewModel.taskEditor.value!!
        assertEquals(editor.startMillis, editor.endMillis) // a moment, with no end to pick
        viewModel.saveTask()

        val stored = repository.tasks.value.single { it.id == state().selectedTaskId }
        assertTrue(stored.isMilestone)
        assertEquals(stored.startMillis, stored.endMillis)
        assertEquals(100, stored.progress)
        assertEquals("Press release at noon", stored.notes)
    }

    @Test
    fun aMilestoneIsDoneOrNot_andSwitchingItOffGivesItAnHour() = runTest {
        timeline()
        viewModel.startEditTask("review") // 16:00–17:00
        viewModel.onTaskProgressChange(40)
        viewModel.onTaskMilestoneChange(true)
        assertEquals(0, viewModel.taskEditor.value!!.progress)
        viewModel.onTaskProgressChange(70)
        assertEquals(0, viewModel.taskEditor.value!!.progress) // the slider isn't shown, but a stray value still snaps
        viewModel.onTaskDoneChange(true)
        assertEquals(100, viewModel.taskEditor.value!!.progress)

        viewModel.onTaskMilestoneChange(false)
        assertEquals(at(17), viewModel.taskEditor.value!!.endMillis)
        assertEquals(100, viewModel.taskEditor.value!!.progress)
    }

    @Test
    fun editTask_savesProgressAndNotes_andCutsNotesAtTheLimit() = runTest {
        timeline()
        viewModel.startEditTask("review")
        viewModel.onTaskProgressChange(130)
        assertEquals(100, viewModel.taskEditor.value!!.progress)
        viewModel.onTaskProgressChange(30)
        viewModel.onTaskNotesChange("x".repeat(2_500))
        assertEquals(2_000, viewModel.taskEditor.value!!.notes.length)
        viewModel.saveTask()

        val stored = repository.tasks.value.single { it.id == "review" }
        assertEquals(30, stored.progress)
        assertEquals(2_000, stored.notes.length)
    }

    @Test
    fun cuttingNotesNeverSplitsAnEmoji() {
        val notes = "x".repeat(1_999) + "\uD83D\uDE80" + "more" // a rocket across the limit
        assertEquals("x".repeat(1_999), cutNotes(notes))
        assertEquals("x".repeat(2_000), cutNotes("x".repeat(2_001)))
    }

    @Test
    fun markDone_savesThroughTheApplyPathAndOffersUndo() = runTest {
        val state = timeline()
        viewModel.selectTask("review")
        viewModel.markSelectedDone()
        assertEquals(100, repository.tasks.value.single { it.id == "review" }.progress)
        assertTrue(state().tasks.single { it.id == "review" }.isDone)
        assertEquals(UndoKind.MarkedDone, viewModel.undoOffer.value?.kind)

        viewModel.undo()
        assertEquals(0, repository.tasks.value.single { it.id == "review" }.progress)
    }

    @Test
    fun searchListsOnlyMatchesAndClosingShowsEverything() = runTest {
        val state = timeline()
        viewModel.openSearch()
        assertEquals(TaskSearchUi(TaskFilter(), matches = 4, total = 4), state().search)

        viewModel.onSearchQuery("test")
        assertEquals(listOf("QA Testing"), state().tasks.map { it.title })
        assertEquals(1, state().search?.matches)
        viewModel.toggleColorFilter(TaskColor.BLUE)
        assertEquals(emptyList<String>(), state().tasks.map { it.title }) // QA Testing is purple
        viewModel.clearSearch()
        assertEquals(4, state().tasks.size)

        viewModel.toggleKindFilter(TaskKind.DONE)
        assertTrue(state().tasks.isEmpty())
        viewModel.closeSearch()
        assertNull(state().search)
        assertEquals(4, state().tasks.size)
    }

    @Test
    fun aWholeSearchCanBeSetAtOnce() = runTest {
        val state = timeline()
        viewModel.selectTask("ui-design")
        viewModel.searchFor("", setOf(TaskColor.PURPLE, TaskColor.AMBER), setOf(TaskKind.NOT_DONE))
        assertEquals(listOf("QA Testing", "Review"), state().tasks.map { it.title })
        assertEquals(TaskFilter("", setOf(TaskColor.PURPLE, TaskColor.AMBER), setOf(TaskKind.NOT_DONE)), state().search?.filter)
        assertNull(state().selectedTaskId) // the search hides it
        assertEquals(false, state().search?.typing) // opened without the keyboard
        // A new one replaces it rather than adding to it.
        viewModel.searchFor("impl", emptySet(), emptySet())
        assertEquals(listOf("Implementation"), state().tasks.map { it.title })
        // Opened by hand, it is for typing.
        viewModel.closeSearch()
        viewModel.openSearch()
        assertEquals(true, state().search?.typing)
    }

    @Test
    fun aSelectedTaskTheSearchHidesIsUnselected() = runTest {
        val state = timeline()
        viewModel.selectTask("review")
        viewModel.openSearch()
        viewModel.onSearchQuery("design")
        assertNull(state().selectedTaskId)
        viewModel.closeSearch()
        assertNull(state().selectedTaskId) // it stays unselected
    }

    @Test
    fun aDragThatBreaksALinkOffersToPushAndUndoRestores() = runTest {
        val state = timeline()
        repository.dependencies.value = listOf(noel.chronova.core.model.TaskDependency("l1", SampleTasks.PROJECT_ID, "ui-design", "implementation"))
        val design = state().tasks.first { it.id == "ui-design" } // 09:00–11:00
        viewModel.commitEdit(design, design.copy(startMillis = at(10), endMillis = at(12)))
        assertEquals(PushOfferUi("UI Design", 1, viewModel.pushOffer.value!!.id), viewModel.pushOffer.value)

        viewModel.pushDependents()
        assertEquals(at(12) to at(15), repository.tasks.value.single { it.id == "implementation" }.let { it.startMillis to it.endMillis })
        assertEquals(UndoKind.PushedDependents, viewModel.undoOffer.value?.kind)
        assertEquals(1, viewModel.undoOffer.value?.count)
        viewModel.undo()
        assertEquals(at(11) to at(14), repository.tasks.value.single { it.id == "implementation" }.let { it.startMillis to it.endMillis })
    }

    @Test
    fun aChangeThatBreaksNoLinkOffersNothing() = runTest {
        val state = timeline()
        repository.dependencies.value = listOf(noel.chronova.core.model.TaskDependency("l1", SampleTasks.PROJECT_ID, "ui-design", "implementation"))
        val design = state().tasks.first { it.id == "ui-design" }
        viewModel.commitEdit(design, design.copy(startMillis = at(8), endMillis = at(10)))
        assertNull(viewModel.pushOffer.value)
        assertNull(viewModel.pushBlocked.value)
    }

    @Test
    fun anEditorSaveThatBreaksALinkOffersToPush() = runTest {
        timeline()
        repository.dependencies.value = listOf(noel.chronova.core.model.TaskDependency("l1", SampleTasks.PROJECT_ID, "implementation", "review"))
        viewModel.startEditTask("implementation") // 11:00–14:00; Review 16:00
        viewModel.onTaskEndChange(at(17))
        viewModel.saveTask()
        assertEquals("Implementation", viewModel.pushOffer.value?.taskTitle)
    }

    @Test
    fun pushingPastTheProjectsEndIsSaidInsteadOfOffered() = runTest {
        val state = timeline()
        repository.dependencies.value = listOf(noel.chronova.core.model.TaskDependency("l1", SampleTasks.PROJECT_ID, "qa-testing", "review"))
        val qa = state().tasks.first { it.id == "qa-testing" } // 13:30–16:00 on the first day; Review 16:00–17:00
        // QA moved to 23:15–23:45 on the last day: Review would then run 23:45–00:45, past the project's end.
        val lastEvening = at(23, 15) + 2 * 24 * 3_600_000L
        viewModel.commitEdit(qa, qa.copy(startMillis = lastEvening, endMillis = lastEvening + 30 * 60_000L))
        assertNull(viewModel.pushOffer.value)
        assertEquals(listOf("Review"), viewModel.pushBlocked.value?.titles)
    }

    @Test
    fun movingTheStartKeepsTheDuration() = runTest {
        timeline()
        viewModel.startEditTask("implementation") // 11:00-14:00
        viewModel.onTaskStartChange(at(12))
        assertEquals(at(15), viewModel.taskEditor.value!!.endMillis)
    }

    @Test
    fun invalidTaskKeepsTheEditorOpen() = runTest {
        timeline()
        viewModel.startEditTask("review")
        viewModel.onTaskEndChange(at(16, 10))
        viewModel.saveTask()
        assertEquals(TaskProblemUi.TooShort(15), viewModel.taskEditor.value?.problem)
        assertTrue(repository.updates.isEmpty())

        viewModel.onTaskEndChange(at(0) + 4 * 24 * 60 * 60_000L) // past the project end
        viewModel.saveTask()
        assertEquals(TaskProblemUi.OutsideProject, viewModel.taskEditor.value?.problem)
    }

    @Test
    fun aTaskThatEndsBeforeItStartsKeepsTheEditorOpen() = runTest {
        timeline()
        viewModel.startEditTask("review") // 16:00–17:00
        viewModel.onTaskEndChange(at(15))
        viewModel.saveTask()
        assertEquals(TaskProblemUi.EndNotAfterStart, viewModel.taskEditor.value?.problem)
        assertTrue(repository.updates.isEmpty())
        // Picking another end clears the problem.
        viewModel.onTaskEndChange(at(18))
        assertNull(viewModel.taskEditor.value?.problem)
    }

    @Test
    fun editTask_savesTitleTimesAndColour() = runTest {
        val state = timeline()
        viewModel.startEditTask("review")
        viewModel.onTaskTitleChange("Final review")
        viewModel.onTaskEndChange(at(18))
        viewModel.onTaskColorChange(TaskColor.PURPLE)
        viewModel.saveTask()
        val review = state().tasks.single { it.id == "review" }
        assertEquals("Final review", review.title)
        assertEquals(at(16), review.startMillis)
        assertEquals(at(18), review.endMillis)
        assertEquals(TaskColor.PURPLE, review.color)
        assertEquals(3, repository.tasks.value.single { it.id == "review" }.sortOrder) // the stored row order
    }

    @Test
    fun deleteThenUndoRestoresTheTask() = runTest {
        val state = timeline()
        val before = state().tasks
        viewModel.startEditTask("qa-testing")
        viewModel.deleteEditingTask()
        assertEquals(listOf("ui-design", "implementation", "review"), state().tasks.map { it.id })
        assertEquals(UndoKind.TaskDeleted, viewModel.undoOffer.value?.kind)

        viewModel.undo()
        assertEquals(before, state().tasks)
        assertNull(viewModel.undoOffer.value)
    }

    @Test
    fun viewSettingsAreSavedAndRestoredPerProject() = runTest {
        val state = timeline()
        viewModel.setTimeScale(TimeScale.WEEKS)
        viewModel.zoomIn()
        val saved = repository.viewSettings[SampleTasks.PROJECT_ID]!!
        assertEquals(TimeScale.WEEKS, saved.timeScale)
        assertEquals(state().config, saved)

        // A new screen for the same project starts from the saved settings.
        viewModel = ProjectTimelineViewModel(SampleTasks.PROJECT_ID, timelineUseCases(repository, zone), zone, today = { today })
        assertEquals(saved, timeline()().config)
    }

    @Test
    fun aPinchZoomsLiveAndIsSavedOnceWhenItEnds() = runTest {
        val state = timeline()
        val start = state().config.unitWidthDp
        val savesBefore = repository.viewSettingsSaves

        viewModel.zoomBy(1.2f)
        viewModel.zoomBy(1.25f)
        assertEquals(start * 1.5f, state().config.unitWidthDp, 0.01f) // live, as the fingers move
        assertEquals(savesBefore, repository.viewSettingsSaves) // nothing written mid-pinch

        viewModel.finishZoom()
        assertEquals(savesBefore + 1, repository.viewSettingsSaves)
        assertEquals(state().config, repository.viewSettings[SampleTasks.PROJECT_ID])

        viewModel.finishZoom() // a second end with no zoom writes nothing
        viewModel.zoomBy(100f)
        assertEquals(TimeScale.HOURS.maxUnitWidthDp, state().config.unitWidthDp) // within the scale's limits
    }

    @Test
    fun predecessorsAreSavedAndCyclesAreForbidden() = runTest {
        val state = timeline()
        // Implementation depends on UI Design.
        viewModel.startEditTask("implementation")
        viewModel.onTogglePredecessor("ui-design")
        viewModel.saveTask()
        assertEquals(listOf("ui-design" to "implementation"), state().dependencies.map { it.predecessorId to it.successorId })

        // UI Design can't now depend on Implementation (cycle), and can't depend on itself.
        viewModel.startEditTask("ui-design")
        val editor = viewModel.taskEditor.value!!
        assertTrue("implementation" in editor.forbiddenPredecessorIds)
        assertTrue("ui-design" in editor.forbiddenPredecessorIds)
        viewModel.onTogglePredecessor("implementation")
        assertTrue(viewModel.taskEditor.value!!.predecessorIds.isEmpty())

        // Unticking removes the link.
        viewModel.dismissTaskEditor()
        viewModel.startEditTask("implementation")
        assertEquals(setOf("ui-design"), viewModel.taskEditor.value!!.predecessorIds)
        viewModel.onTogglePredecessor("ui-design")
        viewModel.saveTask()
        assertTrue(state().dependencies.isEmpty())
    }

    @Test
    fun undoingADeleteRestoresItsLinks() = runTest {
        val state = timeline()
        viewModel.startEditTask("qa-testing")
        viewModel.onTogglePredecessor("implementation")
        viewModel.saveTask()
        viewModel.startEditTask("review")
        viewModel.onTogglePredecessor("qa-testing")
        viewModel.saveTask()
        assertEquals(2, state().dependencies.size)

        viewModel.startEditTask("qa-testing")
        viewModel.deleteEditingTask()
        assertTrue(state().dependencies.isEmpty())

        viewModel.undo()
        assertEquals(2, state().dependencies.size)
    }

    /** Design → Implementation and QA → Review: QA and Review are critical, the others can slip 3 h. */
    private fun linkTheSample() {
        repository.dependencies.value = listOf(
            noel.chronova.core.model.TaskDependency("l1", SampleTasks.PROJECT_ID, "ui-design", "implementation"),
            noel.chronova.core.model.TaskDependency("l2", SampleTasks.PROJECT_ID, "qa-testing", "review"),
        )
    }

    @Test
    fun theCriticalPathIsWorkedOutAndItsSwitchIsRememberedPerProject() = runTest {
        val state = timeline()
        linkTheSample()
        assertEquals(listOf("qa-testing", "review"), state().tasks.filter { it.critical }.map { it.id })
        assertEquals(3 * 3_600_000L, state().tasks.first { it.id == "ui-design" }.slackMillis)
        assertEquals(at(17), state().finishMillis)
        assertEquals(listOf(false, true), state().dependencies.map { it.critical })
        assertEquals(false, state().config.showCriticalPath)

        viewModel.setShowCriticalPath(true)
        assertTrue(state().config.showCriticalPath)
        assertTrue(repository.viewSettings[SampleTasks.PROJECT_ID]!!.showCriticalPath)
        viewModel = ProjectTimelineViewModel(SampleTasks.PROJECT_ID, timelineUseCases(repository, zone), zone, today = { today })
        assertTrue(timeline()().config.showCriticalPath)
    }

    @Test
    fun theEditorSaysHowMuchASavedTaskCanSlipUntilItsTimesChange() = runTest {
        timeline()
        linkTheSample()
        viewModel.startEditTask("implementation")
        assertEquals(SlackUi.CanSlip(3 * 3_600_000L), viewModel.taskEditor.value?.slack)
        viewModel.startEditTask("review")
        assertEquals(SlackUi.OnCriticalPath(at(17)), viewModel.taskEditor.value?.slack)
        viewModel.onTaskStartChange(at(15))
        assertNull(viewModel.taskEditor.value?.slack)
        viewModel.startAddTask()
        assertNull(viewModel.taskEditor.value?.slack)
    }

    @Test
    fun slackIsWordedInItsTwoLargestUnits() {
        val minute = 60_000L
        assertEquals(listOf(DurationParts.Unit.HOURS to 3, DurationParts.Unit.MINUTES to 30), DurationParts.of(210 * minute))
        assertEquals(listOf(DurationParts.Unit.DAYS to 2, DurationParts.Unit.HOURS to 4), DurationParts.of((52 * 60 + 15) * minute))
        assertEquals(listOf(DurationParts.Unit.DAYS to 1), DurationParts.of(24 * 60 * minute + 5 * minute))
        assertEquals(listOf(DurationParts.Unit.MINUTES to 45), DurationParts.of(45 * minute + 59_999))
        assertEquals(listOf(DurationParts.Unit.MINUTES to 0), DurationParts.of(0))
    }

    @Test
    fun undoIsRefusedOnceTheTaskChangedAgain() = runTest {
        val state = timeline()
        viewModel.startEditTask("qa-testing")
        viewModel.deleteEditingTask()
        // Another device's copy of the task arrives by sync before the undo.
        val synced = SampleTasks.tasks(zone)[2].copy(title = "QA (synced)")
        repository.tasks.value = repository.tasks.value + synced

        viewModel.undo()
        assertEquals(TimelineNotice.UndoNotPossible, viewModel.notice.value)
        assertEquals("QA (synced)", state().tasks.first { it.id == "qa-testing" }.title)
    }
}

/** The use cases the app wires, over a fake repository. */
private fun timelineUseCases(repository: ProjectRepository, zone: ZoneId): TimelineUseCases {
    val apply = ApplyScheduleChanges(repository, zone)
    return TimelineUseCases(
        observeSchedule = ObserveProjectSchedule(repository),
        commitEdit = CommitTaskEdit(apply),
        validateTask = ValidateTask(),
        forbiddenPredecessors = ForbiddenPredecessors(),
        saveTask = SaveTask(repository),
        deleteTask = DeleteTask(apply),
        undo = UndoScheduleChanges(apply),
        loadViewSettings = LoadViewSettings(repository),
        saveViewSettings = SaveViewSettings(repository),
        pushDependents = PushDependents(repository, apply, zone),
    )
}
