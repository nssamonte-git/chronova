package noel.chronova.feature.projects.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import noel.chronova.core.designsystem.ChronovaSpacing
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.designsystem.numeric
import noel.chronova.core.designsystem.toColor
import noel.chronova.core.ui.BadgedIconButton
import noel.chronova.core.ui.R as CoreUiR
import noel.chronova.core.ui.TimeFormat
import noel.chronova.core.ui.assistant.AssistantSlot
import noel.chronova.core.ui.components.AuroraFab
import noel.chronova.core.ui.components.BrandTitle
import noel.chronova.core.ui.components.ChronovaCard
import noel.chronova.core.ui.components.ChronovaTopBar
import noel.chronova.core.ui.components.ConfirmDialog
import noel.chronova.core.ui.components.EmptyState
import noel.chronova.core.ui.components.FabClearance
import noel.chronova.core.ui.components.FabColumn
import noel.chronova.core.ui.components.ProgressMeter
import noel.chronova.core.ui.sync.SyncButton
import noel.chronova.core.ui.sync.SyncStatusLine
import noel.chronova.core.ui.sync.SyncUi

@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onOpenProject: (String) -> Unit,
    onOpenAccount: () -> Unit,
    syncUi: SyncUi,
    onSync: () -> Unit,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit,
    /** "Export…" in a project's menu: its id and name (the shell shows the export sheet). */
    onExport: (projectId: String, projectName: String) -> Unit,
    modifier: Modifier = Modifier,
    /** The voice assistant, as the shell builds it (none in previews and tests). */
    assistant: AssistantSlot = AssistantSlot.None,
    /** "Start from: An image…" in the new-project sheet (the shell shows the import sheet); null hides it. */
    onImportImage: (() -> Unit)? = null,
) {
    assistant.screenEffects()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val openProject by viewModel.openProject.collectAsStateWithLifecycle()

    val copyName = stringResource(R.string.project_copy_name) // "Copy of %1$s", filled per project

    // A project was just created: go straight into it.
    LaunchedEffect(openProject) {
        openProject?.let { id ->
            viewModel.onProjectOpened()
            onOpenProject(id)
        }
    }

    ProjectListContent(
        state = state,
        onOpenProject = onOpenProject,
        onCreate = viewModel::startCreate,
        onEdit = viewModel::startEdit,
        onDuplicate = { project -> viewModel.startDuplicate(project, copyName.format(project.name)) },
        onExport = { project -> onExport(project.id, project.name) },
        onDelete = viewModel::requestDelete,
        onOpenAccount = onOpenAccount,
        syncUi = syncUi,
        onSync = onSync,
        unreadNotifications = unreadNotifications,
        onOpenNotifications = onOpenNotifications,
        modifier = modifier,
        assistant = assistant,
    )
    assistant.overlay(ZoneId.systemDefault())
    editor?.let { current ->
        ProjectEditorSheet(
            editor = current,
            onNameChange = viewModel::onNameChange,
            onRangeChange = viewModel::onRangeChange,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
            onStartFrom = viewModel::onStartFrom,
            onFromImage = onImportImage?.let { open ->
                {
                    viewModel.dismissEditor()
                    open()
                }
            },
        )
    }
    pendingDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.project_delete_title, target.name),
            text = if (target.taskCount == 0) {
                stringResource(R.string.project_delete_body_empty)
            } else {
                pluralStringResource(R.plurals.project_delete_body, target.taskCount, target.taskCount)
            },
            confirmLabel = stringResource(CoreUiR.string.delete),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
            destructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectListContent(
    state: ProjectListState,
    onOpenProject: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (ProjectRowUi) -> Unit,
    onDuplicate: (ProjectRowUi) -> Unit,
    onExport: (ProjectRowUi) -> Unit,
    onDelete: (ProjectRowUi) -> Unit,
    onOpenAccount: () -> Unit,
    syncUi: SyncUi,
    onSync: () -> Unit,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    assistant: AssistantSlot = AssistantSlot.None,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val assistantSnackbar by rememberUpdatedState(assistant.snackbar)
    LaunchedEffect(assistant.snackbar?.key) {
        val shown = assistantSnackbar ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            shown.text,
            actionLabel = shown.actionLabel,
            duration = if (shown.long) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        shown.onResult(result == SnackbarResult.ActionPerformed)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FabColumn {
                // The assistant's orb, just above "New project", as on the timeline.
                if (assistant.available) assistant.orbButton(Modifier)
                AuroraFab(stringResource(R.string.project_new), onClick = onCreate) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        },
        topBar = {
            ChronovaTopBar(
                title = { BrandTitle(stringResource(R.string.app_tagline)) },
                actions = {
                    NotificationsButton(unreadNotifications, onOpenNotifications)
                    if (syncUi.signedIn) SyncButton(syncUi, onSync)
                    IconButton(onClick = onOpenAccount) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.account_open))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SyncStatusLine(syncUi, onClick = onOpenAccount)
            Box(Modifier.fillMaxSize()) {
                when (state) {
                    ProjectListState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is ProjectListState.Loaded ->
                        if (state.projects.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.projects_empty_title),
                                body = stringResource(R.string.projects_empty),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            LazyColumn(
                                // Bottom padding keeps the last card clear of the FAB (and the orb above it).
                                contentPadding = PaddingValues(
                                    start = ChronovaSpacing.screenGutter,
                                    top = ChronovaSpacing.s,
                                    end = ChronovaSpacing.screenGutter,
                                    bottom = if (assistant.available) FabClearance.withOrb else FabClearance.single,
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.projects, key = { it.id }) { summary ->
                                    ProjectCard(
                                        summary = summary,
                                        onClick = { onOpenProject(summary.id) },
                                        onEdit = { onEdit(summary) },
                                        onDuplicate = { onDuplicate(summary) },
                                        onExport = { onExport(summary) },
                                        onDelete = { onDelete(summary) },
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    summary: ProjectRowUi,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    ChronovaCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(start = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = summary.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = TimeFormat.dateRange(summary.startDate, summary.endDate),
                        style = MaterialTheme.typography.bodyMedium.numeric(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProjectMenu(projectName = summary.name, onEdit = onEdit, onDuplicate = onDuplicate, onExport = onExport, onDelete = onDelete)
            }
            if (summary.bars.isNotEmpty()) {
                MiniChart(summary.bars, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth())
            }
            // The count in words and, beside it, how much is done as a short bar (apart from the
            // chart above, so it isn't read as one more task).
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val tasks = pluralStringResource(R.plurals.task_count, summary.taskCount, summary.taskCount)
                Text(
                    text = if (summary.doneCount > 0) pluralStringResource(R.plurals.task_count_done, summary.doneCount, tasks, summary.doneCount) else tasks,
                    style = MaterialTheme.typography.labelMedium.numeric(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (summary.taskCount > 0) {
                    ProgressMeter(
                        fraction = summary.doneCount.toFloat() / summary.taskCount,
                        modifier = Modifier.size(width = 72.dp, height = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * The project's first tasks as a small chart: one thin bar per task in its colour, placed
 * along the project's days, and a dot for a milestone. A picture of the project's shape, not
 * something to read: the dates and the count beside it say the same in words, so it has no
 * description of its own.
 */
@Composable
private fun MiniChart(bars: List<ProjectBarUi>, modifier: Modifier = Modifier) {
    val colors = bars.map { it.color.toColor() }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier.height(MiniBarHeight * bars.size + MiniBarGap * (bars.size - 1))) {
        val barHeight = MiniBarHeight.toPx()
        val step = barHeight + MiniBarGap.toPx()
        val radius = CornerRadius(barHeight / 2f)
        bars.forEachIndexed { row, bar ->
            val top = row * step
            // A faint track for each row, so a short bar still reads as "part of this length".
            drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
            if (bar.milestone) {
                val x = (size.width * bar.start).coerceIn(barHeight / 2f, size.width - barHeight / 2f)
                drawCircle(colors[row], radius = barHeight * 0.8f, center = Offset(x, top + barHeight / 2f))
            } else {
                // Never thinner than a dot, however short the task is against a long project.
                val width = (size.width * (bar.end - bar.start)).coerceAtLeast(barHeight)
                val left = (size.width * bar.start).coerceAtMost(size.width - width)
                drawRoundRect(colors[row], topLeft = Offset(left, top), size = Size(width, barHeight), cornerRadius = radius)
            }
        }
    }
}

private val MiniBarHeight = 5.dp
private val MiniBarGap = 4.dp

@Composable
private fun ProjectMenu(projectName: String, onEdit: () -> Unit, onDuplicate: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(4.dp)) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.project_actions, projectName))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.duplicate)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null) },
                onClick = {
                    expanded = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export)) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = {
                    expanded = false
                    onExport()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(CoreUiR.string.delete)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ProjectListPreview() {
    ChronovaTheme {
        ProjectListContent(
            state = ProjectListState.Loaded(
                listOf(ProjectRowUi("sample", "Sample project", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4), taskCount = 4)),
            ),
            onOpenProject = {},
            onCreate = {},
            onEdit = {},
            onDuplicate = {},
            onExport = {},
            onDelete = {},
            onOpenAccount = {},
            syncUi = SyncUi(),
            onSync = {},
            unreadNotifications = 2,
            onOpenNotifications = {},
        )
    }
}

/** The bell: opens the notifications page; the badge counts unread ones. */
@Composable
private fun NotificationsButton(unread: Int, onClick: () -> Unit) {
    val description = if (unread > 0) {
        pluralStringResource(R.plurals.notifications_open, unread, unread)
    } else {
        stringResource(R.string.notifications_open_none)
    }
    BadgedIconButton(count = unread, onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(Icons.Filled.Notifications, contentDescription = null)
    }
}
