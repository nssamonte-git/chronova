package noel.chronova.app.presentation.timeline

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.Settings
import android.net.Uri
import android.content.pm.PackageManager
import android.content.Intent
import android.Manifest
import androidx.compose.foundation.layout.size
import noel.chronova.app.voice.orb.OrbMode
import noel.chronova.app.presentation.voice.AssistantOrb
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneOffset
import noel.chronova.app.R
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TimeScale
import noel.chronova.app.presentation.components.SchedulerTimeline
import noel.chronova.app.presentation.components.TimelineControls
import noel.chronova.app.presentation.TimeFormat
import noel.chronova.app.presentation.voice.AssistantAvailability
import noel.chronova.app.presentation.voice.AssistantMessage
import noel.chronova.app.presentation.voice.AssistantOverlay
import noel.chronova.app.presentation.voice.AssistantNotice
import noel.chronova.app.presentation.voice.AssistantViewModel
import noel.chronova.app.ui.theme.ChronovaTheme

@Composable
fun ProjectTimelineScreen(
    viewModel: ProjectTimelineViewModel,
    assistant: AssistantViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val assistantState by assistant.state.collectAsStateWithLifecycle()
    val assistantMessage by assistant.message.collectAsStateWithLifecycle()
    val highlighted by assistant.highlight.collectAsStateWithLifecycle()
    val micLevel = assistant.level
    val context = LocalContext.current
    var showNotice by remember { mutableStateOf(false) }
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) assistant.open() else assistant.onMicrophoneDenied()
    }
    // Tap the orb: the one-time notice, then the microphone permission, then the orb opens and listens.
    val startAssistant = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            assistant.open()
        } else {
            askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val onOrbTap = { if (assistantState.noticeAccepted) startAssistant() else showNotice = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { assistant.onLeftApp() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val taskEditor by viewModel.taskEditor.collectAsStateWithLifecycle()
    val undoOffer by viewModel.undoOffer.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    when (val current = state) {
        TimelineScreenState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        // The project was deleted (e.g. by a sync) while open: leave the screen.
        TimelineScreenState.NotFound -> LaunchedEffect(Unit) { onBack() }
        is TimelineScreenState.Ready -> {
            TimelineContent(
                state = current.timeline,
                actions = TimelineActions(
                    onBack = onBack,
                    onSelectTask = viewModel::selectTask,
                    onCommitEdit = viewModel::commitEdit,
                    onTimeScale = viewModel::setTimeScale,
                    onZoomIn = viewModel::zoomIn,
                    onZoomOut = viewModel::zoomOut,
                    onZoomReset = viewModel::resetZoom,
                    onSnap = viewModel::setSnapMinutes,
                    onRowHeightStep = viewModel::changeRowHeight,
                    onAddTask = viewModel::startAddTask,
                    onEditTask = viewModel::startEditTask,
                    onUndo = viewModel::undo,
                    onUndoShown = viewModel::dismissUndo,
                    onNoticeShown = viewModel::dismissNotice,
                    onOpenAssistant = onOrbTap,
                    onAssistantUndo = assistant::undo,
                    onAssistantMessageShown = assistant::dismissMessage,
                    onOpenAppSettings = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    },
                ),
                assistantAvailable = assistantState.availability != AssistantAvailability.NOT_CONFIGURED,
                assistantOpen = assistantState.open,
                assistantMessage = assistantMessage,
                highlighted = highlighted,
                undoOffer = undoOffer,
                notice = notice,
                modifier = modifier,
            )
            AssistantOverlay(
                state = assistantState,
                zone = current.timeline.zone,
                level = { micLevel.value },
                onClose = assistant::close,
            )
            if (showNotice) {
                AssistantNotice(
                    onContinue = {
                        showNotice = false
                        assistant.acceptNotice()
                        startAssistant()
                    },
                    onDismiss = { showNotice = false },
                )
            }
            taskEditor?.let { editor ->
                TaskEditorSheet(
                    editor = editor,
                    otherTasks = current.timeline.tasks.filter { it.id != editor.taskId },
                    window = current.timeline.window,
                    zone = current.timeline.zone,
                    onTogglePredecessor = viewModel::onTogglePredecessor,
                    onTitleChange = viewModel::onTaskTitleChange,
                    onColorChange = viewModel::onTaskColorChange,
                    onStartChange = viewModel::onTaskStartChange,
                    onEndChange = viewModel::onTaskEndChange,
                    onSave = viewModel::saveTask,
                    onDelete = viewModel::deleteEditingTask,
                    onDismiss = viewModel::dismissTaskEditor,
                )
            }
        }
    }
}

/** UI intents, grouped to keep composable signatures short. */
data class TimelineActions(
    val onBack: () -> Unit = {},
    val onSelectTask: (String?) -> Unit = {},
    val onCommitEdit: (original: SchedulerTask, edited: SchedulerTask) -> Unit = { _, _ -> },
    val onTimeScale: (TimeScale) -> Unit = {},
    val onZoomIn: () -> Unit = {},
    val onZoomOut: () -> Unit = {},
    val onZoomReset: () -> Unit = {},
    val onSnap: (Int) -> Unit = {},
    val onRowHeightStep: (Int) -> Unit = {},
    val onAddTask: () -> Unit = {},
    val onEditTask: (String) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onUndoShown: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
    val onOpenAssistant: () -> Unit = {},
    val onAssistantUndo: () -> Unit = {},
    val onAssistantMessageShown: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
)

@Composable
private fun TimelineContent(
    state: TimelineUiState,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
    undoOffer: UndoOffer? = null,
    notice: TimelineNotice? = null,
    assistantAvailable: Boolean = false,
    assistantOpen: Boolean = false,
    assistantMessage: AssistantMessage? = null,
    highlighted: Set<String> = emptySet(),
) {
    var showControls by rememberSaveable { mutableStateOf(true) }
    val snackbarHost = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.task_deleted)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(undoOffer) {
        val offer = undoOffer ?: return@LaunchedEffect
        val message = when (offer.kind) {
            UndoKind.TaskDeleted -> deletedMessage
        }
        val result = snackbarHost.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) actions.onUndo() else actions.onUndoShown()
    }
    val resources = LocalResources.current
    LaunchedEffect(assistantMessage) {
        when (val shown = assistantMessage ?: return@LaunchedEffect) {
            is AssistantMessage.Applied -> {
                val text = resources.getQuantityString(R.plurals.assistant_applied, shown.taskCount, shown.taskCount)
                val result = snackbarHost.showSnackbar(text, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) actions.onAssistantUndo() else actions.onAssistantMessageShown()
            }
            AssistantMessage.UndoNotPossible -> {
                snackbarHost.showSnackbar(resources.getString(R.string.undo_not_possible), duration = SnackbarDuration.Short)
                actions.onAssistantMessageShown()
            }
            AssistantMessage.NeedsConnection -> {
                snackbarHost.showSnackbar(resources.getString(R.string.assistant_needs_connection), duration = SnackbarDuration.Short)
                actions.onAssistantMessageShown()
            }
            AssistantMessage.NeedsSignIn -> {
                snackbarHost.showSnackbar(resources.getString(R.string.assistant_needs_sign_in), duration = SnackbarDuration.Short)
                actions.onAssistantMessageShown()
            }
            AssistantMessage.MicrophoneDenied -> {
                val result = snackbarHost.showSnackbar(
                    resources.getString(R.string.assistant_mic_denied),
                    actionLabel = resources.getString(R.string.assistant_open_settings),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) actions.onOpenAppSettings()
                actions.onAssistantMessageShown()
            }
        }
    }
    val editNotSavedMessage = stringResource(R.string.edit_not_saved)
    val undoNotPossibleMessage = stringResource(R.string.undo_not_possible)
    LaunchedEffect(notice) {
        val shown = notice ?: return@LaunchedEffect
        val message = when (shown) {
            TimelineNotice.EditNotSaved -> editNotSavedMessage
            TimelineNotice.UndoNotPossible -> undoNotPossibleMessage
        }
        snackbarHost.showSnackbar(message, duration = SnackbarDuration.Short)
        actions.onNoticeShown()
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TimelineTopBar(
                title = state.projectName,
                controlsVisible = showControls,
                selectedTaskId = state.selectedTaskId,
                assistantAvailable = assistantAvailable,
                assistantOpen = assistantOpen,
                onOpenAssistant = actions.onOpenAssistant,
                onBack = actions.onBack,
                onEditTask = actions.onEditTask,
                onToggleControls = { showControls = !showControls },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAddTask,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.task_add)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnimatedVisibility(visible = showControls) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TimelineControls(
                        config = state.config,
                        onTimeScale = actions.onTimeScale,
                        onZoomIn = actions.onZoomIn,
                        onZoomOut = actions.onZoomOut,
                        onZoomReset = actions.onZoomReset,
                        onSnap = actions.onSnap,
                        onRowHeightStep = actions.onRowHeightStep,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SchedulerTimeline(
                state = state,
                onSelectTask = actions.onSelectTask,
                onCommitEdit = actions.onCommitEdit,
                onEditTask = actions.onEditTask,
                highlighted = highlighted,
                modifier = Modifier.weight(1f),
                // Lets the last rows scroll clear of the FAB.
                contentPadding = PaddingValues(bottom = 88.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineTopBar(
    title: String,
    controlsVisible: Boolean,
    selectedTaskId: String?,
    assistantAvailable: Boolean,
    assistantOpen: Boolean,
    onOpenAssistant: () -> Unit,
    onBack: () -> Unit,
    onEditTask: (String) -> Unit,
    onToggleControls: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to projects")
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            if (assistantAvailable) {
                val assistantLabel = stringResource(R.string.assistant_open)
                IconButton(onClick = onOpenAssistant, modifier = Modifier.semantics { contentDescription = assistantLabel }) {
                    // The live 3D orb; the plain mic icon if this device can't render it.
                    // Paused while the big orb is open over the timeline.
                    AssistantOrb(mode = OrbMode.Idle, active = !assistantOpen, modifier = Modifier.size(34.dp)) {
                        Icon(painterResource(R.drawable.ic_mic), contentDescription = null)
                    }
                }
            }
            if (selectedTaskId != null) {
                IconButton(onClick = { onEditTask(selectedTaskId) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.task_edit_selected))
                }
            }
            IconButton(onClick = onToggleControls) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = if (controlsVisible) "Hide timeline settings" else "Show timeline settings",
                    tint = if (controlsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TimelineContentPreview() {
    ChronovaTheme {
        TimelineContent(state = TimelineUiState.sample(ZoneOffset.UTC), actions = TimelineActions())
    }
}
