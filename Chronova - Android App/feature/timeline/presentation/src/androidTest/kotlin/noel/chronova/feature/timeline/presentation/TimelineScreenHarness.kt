package noel.chronova.feature.timeline.presentation

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.model.TimelineConfig
import noel.chronova.core.testing.FakeProjectRepository
import noel.chronova.core.ui.TimeFormat
import noel.chronova.core.ui.assistant.AssistantSlot
import noel.chronova.feature.timeline.domain.CommitTaskEdit
import noel.chronova.feature.timeline.domain.DeleteTask
import noel.chronova.feature.timeline.domain.ForbiddenPredecessors
import noel.chronova.feature.timeline.domain.LoadViewSettings
import noel.chronova.feature.timeline.domain.ObserveProjectSchedule
import noel.chronova.feature.timeline.domain.SaveTask
import noel.chronova.feature.timeline.domain.SaveViewSettings
import noel.chronova.feature.timeline.domain.TimelineUseCases
import noel.chronova.feature.timeline.domain.UndoScheduleChanges
import noel.chronova.feature.timeline.domain.ValidateTask

/**
 * Renders the real timeline screen from the real [ProjectTimelineViewModel] over an in-memory
 * repository, in UTC, for the on-device screen tests. Tests talk to it in visible text only.
 */
class TimelineScreenHarness(private val compose: ComposeContentTestRule) {
    val res = InstrumentationRegistry.getInstrumentation().targetContext.resources!!
    val zone: ZoneOffset = ZoneOffset.UTC

    /** Five days, 2–6 November 2026; "today" is the 3rd. */
    val project = Project(PROJECT_ID, "Fit-out", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6))
    val today: LocalDate = LocalDate.of(2026, 11, 3)

    lateinit var repository: FakeProjectRepository
        private set

    fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).toInstant(zone).toEpochMilli()

    fun task(id: String, title: String, start: Long, end: Long, order: Int) =
        SchedulerTask(id, PROJECT_ID, title, start, end, TaskColor.entries[order % TaskColor.entries.size], order)

    fun link(predecessor: String, successor: String) = TaskDependency("dep-$predecessor-$successor", PROJECT_ID, predecessor, successor)

    /** Shows the screen; [config] is the project's saved view settings (hours at the default zoom unless given). */
    fun show(
        tasks: List<SchedulerTask>,
        dependencies: List<TaskDependency> = emptyList(),
        config: TimelineConfig = TimelineConfig(),
    ) {
        repository = FakeProjectRepository(listOf(project), tasks).also {
            it.dependencies.value = dependencies
            it.viewSettings[PROJECT_ID] = config
        }
        val apply = ApplyScheduleChanges(repository, zone)
        val viewModel = ProjectTimelineViewModel(
            PROJECT_ID,
            TimelineUseCases(
                observeSchedule = ObserveProjectSchedule(repository),
                commitEdit = CommitTaskEdit(apply),
                validateTask = ValidateTask(),
                forbiddenPredecessors = ForbiddenPredecessors(),
                saveTask = SaveTask(repository),
                deleteTask = DeleteTask(apply),
                undo = UndoScheduleChanges(apply),
                loadViewSettings = LoadViewSettings(repository),
                saveViewSettings = SaveViewSettings(repository),
            ),
            zone,
            today = { today },
        )
        compose.setContent {
            ChronovaTheme { ProjectTimelineScreen(viewModel, AssistantSlot.None, onBack = {}) }
        }
        compose.waitForIdle()
    }

    fun string(id: Int): String = res.getString(id)

    /** The row's name cell for [title]: the clickable cell holding the title (the bar's title isn't clickable). */
    fun nameCell(title: String): SemanticsNodeInteraction = compose.onNode(hasText(title) and hasClickAction())

    /** The time range a row's name cell shows. */
    fun range(start: Long, end: Long): String = TimeFormat.range(start, end, zone)

    fun button(text: String): SemanticsNodeInteraction = compose.onNode(hasText(text) and hasClickAction())

    /**
     * The "Add task" FAB. Material's extended FAB hides its label from the merged tree
     * (clearAndSetSemantics), so the label is found in the unmerged tree and tapped there.
     */
    fun addTaskButton(): SemanticsNodeInteraction = compose.onNodeWithText(string(R.string.task_add), useUnmergedTree = true)

    companion object {
        const val PROJECT_ID = "fit-out"
    }
}
