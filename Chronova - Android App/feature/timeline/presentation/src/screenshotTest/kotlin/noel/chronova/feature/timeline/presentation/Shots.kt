package noel.chronova.feature.timeline.presentation

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import java.time.ZoneOffset
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TimeScale
import noel.chronova.core.model.TimelineConfig
import noel.chronova.core.model.TimelineWindow

private val zone = ZoneOffset.UTC
private val sample = TimelineUiState.sample(zone)

/** The sample project's timeline. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun WithTasks() = ChronovaTheme { TimelineContent(state = sample, actions = TimelineActions()) }

/** One task done, one under way, one not started, and a selected milestone, all in the first hours on screen. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ProgressAndMilestone() {
    fun at(hour: Int, minute: Int = 0) = sample.window.startMillis + hour * 3_600_000L + minute * 60_000L
    val tasks = listOf(
        TaskUi("design", "Design", at(7), at(7, 45), TaskColor.BLUE, progress = 100),
        TaskUi("build", "Build", at(7, 15), at(9, 15), TaskColor.GREEN, progress = 40),
        TaskUi("test", "Test", at(8), at(9, 30), TaskColor.PURPLE),
        TaskUi("launch", "Launch", at(8, 15), at(8, 15), TaskColor.AMBER, isMilestone = true),
    )
    ChronovaTheme {
        TimelineContent(
            state = sample.copy(tasks = tasks, config = sample.config.copy(rowHeightDp = 56f), selectedTaskId = "launch"),
            actions = TimelineActions(),
            now = { at(7, 30) }, // the now line
        )
    }
}

/**
 * The critical path shown: UI Design → Implementation and QA Testing → Review are linked,
 * so QA Testing and Review set the finish (red outline, darker arrow) and the others fade.
 */
@PreviewTest
@ChronovaScreenshots
@Composable
fun CriticalPathShown() {
    val hour = 3_600_000L
    val slack = mapOf("ui-design" to 3 * hour, "implementation" to 3 * hour, "qa-testing" to 0L, "review" to 0L)
    ChronovaTheme {
        TimelineContent(
            state = sample.copy(
                tasks = sample.tasks.map { it.copy(slackMillis = slack[it.id]) },
                dependencies = listOf(
                    DependencyUi("ui-design", "implementation"),
                    DependencyUi("qa-testing", "review", critical = true),
                ),
                // Days at the widest zoom-out, so the whole first day (all four tasks) is on screen.
                config = TimelineConfig(TimeScale.DAYS, unitWidthDp = TimeScale.DAYS.minUnitWidthDp, showCriticalPath = true),
            ),
            actions = TimelineActions(),
        )
    }
}

/**
 * Months across a month's start: "Dec 2026" on 1 Dec wins over "30 Nov" (a Monday the day before),
 * which is left out rather than drawn into it. Whole-day tasks (from a chart) list their days, not "00:00 – 00:00".
 */
@PreviewTest
@ChronovaScreenshots
@Composable
fun MonthsWholeDays() {
    val day = 86_400_000L
    val start = java.time.LocalDate.of(2026, 11, 23).atStartOfDay(zone).toInstant().toEpochMilli()
    fun task(id: String, title: String, from: Int, days: Int, color: TaskColor) = TaskUi(id, title, start + from * day, start + (from + days) * day, color)
    ChronovaTheme {
        TimelineContent(
            state = sample.copy(
                projectName = "Project Timeline",
                window = TimelineWindow(start, start + 49 * day),
                tasks = listOf(
                    task("t1", "Initial Client Consult", 0, 7, TaskColor.BLUE),
                    task("t2", "Project Design Inquiry", 0, 14, TaskColor.GREEN),
                    task("t3", "Initial Design Concept", 14, 7, TaskColor.PURPLE),
                    task("t4", "Release Design For Bid", 21, 7, TaskColor.AMBER),
                ),
                config = TimelineConfig(TimeScale.MONTHS, rowHeightDp = 56f), // its default zoom, where Mondays get labels
            ),
            actions = TimelineActions(),
        )
    }
}

/** Searching: the bar with a query and a chip on, and only the match listed. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun Searching() {
    val filter = TaskFilter(query = "test", kinds = setOf(TaskKind.NOT_DONE))
    val matches = sample.tasks.filter(filter::matches)
    ChronovaTheme {
        TimelineContent(
            state = sample.copy(tasks = matches, search = TaskSearchUi(filter, matches = matches.size, total = sample.tasks.size)),
            actions = TimelineActions(),
        )
    }
}

/** A search with no match. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun NoMatch() = ChronovaTheme {
    TimelineContent(
        state = sample.copy(tasks = emptyList(), search = TaskSearchUi(TaskFilter(query = "roof"), matches = 0, total = sample.tasks.size)),
        actions = TimelineActions(),
    )
}

/** A project with no tasks yet. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun Empty() = ChronovaTheme { TimelineContent(state = sample.copy(tasks = emptyList()), actions = TimelineActions()) }

@Composable
private fun Editor(editor: TaskEditorState) = ChronovaTheme {
    TaskEditorSheet(
        editor = editor,
        otherTasks = sample.tasks.filter { it.id != editor.taskId },
        window = sample.window,
        zone = zone,
        onTogglePredecessor = {},
        onTitleChange = {},
        onColorChange = {},
        onStartChange = {},
        onEndChange = {},
        onMilestoneChange = {},
        onProgressChange = {},
        onDoneChange = {},
        onNotesChange = {},
        onSave = {},
        onDelete = {},
        onDismiss = {},
    )
}

/** The add-task sheet. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun TaskEditorAdding() {
    val start = sample.window.startMillis + 9 * 3_600_000L
    Editor(TaskEditorState(taskId = null, title = "Stand-up", startMillis = start, endMillis = start + 3_600_000L, color = TaskColor.GREEN))
}

/** Editing a task under way, with notes. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun TaskEditorProgressAndNotes() {
    val task = sample.tasks[1]
    Editor(
        TaskEditorState(
            taskId = task.id,
            title = task.title,
            startMillis = task.startMillis,
            endMillis = task.endMillis,
            color = task.color,
            progress = 40,
            notes = "Waiting on the API review.\nShip behind a flag.",
            slack = SlackUi.CanSlip(3 * 3_600_000L + 30 * 60_000L, working = true),
        ),
    )
}

/** Editing a task on the critical path: the finish it holds up, in red. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun TaskEditorOnTheCriticalPath() {
    val task = sample.tasks.last()
    Editor(
        TaskEditorState(
            taskId = task.id,
            title = task.title,
            startMillis = task.startMillis,
            endMillis = task.endMillis,
            color = task.color,
            slack = SlackUi.OnCriticalPath(task.endMillis),
        ),
    )
}

/** Adding a milestone: one time, and done or not. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun TaskEditorMilestone() {
    val at = sample.window.startMillis + 18 * 3_600_000L
    Editor(TaskEditorState(taskId = null, title = "Launch", startMillis = at, endMillis = at, color = TaskColor.AMBER, isMilestone = true))
}

/** The edit sheet refusing a task that ends before it starts. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun TaskEditorEndBeforeStart() {
    val task = sample.tasks.last()
    Editor(
        TaskEditorState(
            taskId = task.id,
            title = task.title,
            startMillis = task.startMillis,
            endMillis = task.startMillis - 3_600_000L,
            color = task.color,
            predecessorIds = setOf(sample.tasks.first().id),
            problem = TaskProblemUi.EndNotAfterStart,
        ),
    )
}

/** The task editor's time picker. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun TaskTimePicker() = ChronovaTheme {
    TimeDialog(millis = java.time.LocalDateTime.of(2026, 3, 2, 9, 30).toInstant(java.time.ZoneOffset.UTC).toEpochMilli(), zone = java.time.ZoneOffset.UTC, onConfirm = {}, onDismiss = {})
}
