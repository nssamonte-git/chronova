package noel.chronova.feature.timeline.presentation

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import java.time.ZoneOffset
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.model.TaskColor

private val zone = ZoneOffset.UTC
private val sample = TimelineUiState.sample(zone)

/** The sample project's timeline. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun TimelineWithTasks() = ChronovaTheme { TimelineContent(state = sample, actions = TimelineActions()) }

/** One task done, one under way, one not started, and a selected milestone, all in the first hours on screen. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun TimelineWithProgressAndAMilestone() {
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

/** Searching: the bar with a query and a chip on, and only the match listed. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun TimelineSearching() {
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
fun TimelineNoMatch() = ChronovaTheme {
    TimelineContent(
        state = sample.copy(tasks = emptyList(), search = TaskSearchUi(TaskFilter(query = "roof"), matches = 0, total = sample.tasks.size)),
        actions = TimelineActions(),
    )
}

/** A project with no tasks yet. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun TimelineEmpty() = ChronovaTheme { TimelineContent(state = sample.copy(tasks = emptyList()), actions = TimelineActions()) }

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
