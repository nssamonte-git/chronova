package noel.chronova.feature.projects.presentation

import androidx.compose.material.icons.filled.Share
import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import noel.chronova.core.ui.BadgedIconButton
import noel.chronova.core.ui.TimeFormat
import noel.chronova.core.ui.sync.SyncButton
import noel.chronova.core.ui.sync.SyncStatusLine
import noel.chronova.core.ui.sync.SyncUi
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.ui.R as CoreUiR

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
) {
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
    )
    editor?.let { current ->
        ProjectEditorSheet(
            editor = current,
            onNameChange = viewModel::onNameChange,
            onRangeChange = viewModel::onRangeChange,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
            onStartFrom = viewModel::onStartFrom,
        )
    }
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.project_delete_title, target.name)) },
            text = {
                Text(
                    if (target.taskCount == 0) {
                        stringResource(R.string.project_delete_body_empty)
                    } else {
                        pluralStringResource(R.plurals.project_delete_body, target.taskCount, target.taskCount)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(CoreUiR.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(CoreUiR.string.cancel)) } },
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
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            val label = stringResource(R.string.project_new)
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(label) },
                // Material's extended FAB hides its text from accessibility; TalkBack reads this instead.
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(CoreUiR.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
                            Text(
                                text = stringResource(R.string.projects_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                            )
                        } else {
                            LazyColumn(
                                // Bottom padding keeps the last card clear of the FAB.
                                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ProjectCardText(summary, Modifier.weight(1f))
            ProjectMenu(projectName = summary.name, onEdit = onEdit, onDuplicate = onDuplicate, onExport = onExport, onDelete = onDelete)
        }
    }
}

@Composable
private fun ProjectCardText(summary: ProjectRowUi, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = summary.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = TimeFormat.dateRange(summary.startDate, summary.endDate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val tasks = pluralStringResource(R.plurals.task_count, summary.taskCount, summary.taskCount)
        Text(
            text = if (summary.doneCount > 0) pluralStringResource(R.plurals.task_count_done, summary.doneCount, tasks, summary.doneCount) else tasks,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
