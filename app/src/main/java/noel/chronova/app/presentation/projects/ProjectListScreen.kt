package noel.chronova.app.presentation.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import noel.chronova.app.R
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.model.ProjectSummary
import noel.chronova.app.presentation.TimeFormat
import noel.chronova.app.ui.theme.ChronovaTheme

@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onOpenProject: (String) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val openProject by viewModel.openProject.collectAsStateWithLifecycle()

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
        onEdit = { viewModel.startEdit(it.project) },
        onDelete = viewModel::requestDelete,
        onOpenAccount = onOpenAccount,
        modifier = modifier,
    )
    editor?.let { current ->
        ProjectEditorSheet(
            editor = current,
            onNameChange = viewModel::onNameChange,
            onRangeChange = viewModel::onRangeChange,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
        )
    }
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.project_delete_title, target.project.name)) },
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
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectListContent(
    state: ProjectListState,
    onOpenProject: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (ProjectSummary) -> Unit,
    onDelete: (ProjectSummary) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.project_new)) },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
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
                    IconButton(onClick = onOpenAccount) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.account_open))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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
                            items(state.projects, key = { it.project.id }) { summary ->
                                ProjectCard(
                                    summary = summary,
                                    onClick = { onOpenProject(summary.project.id) },
                                    onEdit = { onEdit(summary) },
                                    onDelete = { onDelete(summary) },
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun ProjectCard(summary: ProjectSummary, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ProjectCardText(summary, Modifier.weight(1f))
            ProjectMenu(projectName = summary.project.name, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@Composable
private fun ProjectCardText(summary: ProjectSummary, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = summary.project.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = TimeFormat.dateRange(summary.project.startDate, summary.project.endDate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pluralStringResource(R.plurals.task_count, summary.taskCount, summary.taskCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProjectMenu(projectName: String, onEdit: () -> Unit, onDelete: () -> Unit) {
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
                text = { Text(stringResource(R.string.delete)) },
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
            state = ProjectListState.Loaded(listOf(ProjectSummary(SampleTasks.project, taskCount = 4))),
            onOpenProject = {},
            onCreate = {},
            onEdit = {},
            onDelete = {},
            onOpenAccount = {},
        )
    }
}
