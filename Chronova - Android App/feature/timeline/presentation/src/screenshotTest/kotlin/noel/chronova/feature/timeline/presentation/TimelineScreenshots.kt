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
