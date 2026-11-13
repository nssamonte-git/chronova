package noel.chronova.feature.projects.presentation

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.model.TaskColor
import noel.chronova.core.ui.sync.SyncPhase
import noel.chronova.core.ui.sync.SyncUi

private val projects = listOf(
    ProjectRowUi(
        "office", "Office move", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 20), taskCount = 3, doneCount = 1,
        bars = listOf(
            ProjectBarUi(0f, 0.3f, TaskColor.BLUE),
            ProjectBarUi(0.25f, 0.7f, TaskColor.GREEN),
            ProjectBarUi(0.85f, 0.85f, TaskColor.AMBER, milestone = true),
        ),
    ),
    ProjectRowUi(
        "launch", "Website launch", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 14), taskCount = 1,
        bars = listOf(ProjectBarUi(0.4f, 0.42f, TaskColor.PURPLE)),
    ),
    ProjectRowUi("kitchen", "Kitchen", LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 28), taskCount = 0),
)

@Composable
private fun ProjectList(state: ProjectListState, sync: SyncUi) = ChronovaTheme {
    ProjectListContent(
        state = state,
        onOpenProject = {},
        onCreate = {},
        onEdit = {},
        onDuplicate = {},
        onExport = {},
        onDelete = {},
        onOpenAccount = {},
        syncUi = sync,
        onSync = {},
        unreadNotifications = 2,
        onOpenNotifications = {},
    )
}

/** The project list with three projects, synced. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ProjectListWithProjects() = ProjectList(ProjectListState.Loaded(projects), SyncUi(configured = true, signedIn = true, phase = SyncPhase.Synced))

/** The empty project list, with changes waiting to sync. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ProjectListEmpty() = ProjectList(ProjectListState.Loaded(emptyList()), SyncUi(configured = true, signedIn = true, pending = 4))

/** The new-project sheet. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ProjectEditorNew() = ChronovaTheme {
    ProjectEditorSheet(
        editor = ProjectEditorState(null, "Kitchen", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 26)),
        onNameChange = {},
        onRangeChange = { _, _ -> },
        onSave = {},
        onDismiss = {},
    )
}

/** Copying a project: "Start from" names it, only the start is picked, and the length is kept. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ProjectEditorCopy() = ChronovaTheme {
    val office = ProjectSourceUi("office", "Office move", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 20))
    ProjectEditorSheet(
        editor = ProjectEditorState(
            null, "Copy of Office move", LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 25),
            copyFrom = office, sources = listOf(office),
        ),
        onNameChange = {},
        onRangeChange = { _, _ -> },
        onSave = {},
        onDismiss = {},
    )
}

/** The project sheet refusing a range that ends before it starts. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ProjectEditorEndBeforeStart() = ChronovaTheme {
    ProjectEditorSheet(
        editor = ProjectEditorState("office", "Office move", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 10), problem = ProjectProblemUi.EndBeforeStart),
        onNameChange = {},
        onRangeChange = { _, _ -> },
        onSave = {},
        onDismiss = {},
    )
}
