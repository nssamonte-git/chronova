package noel.chronova.feature.timeline.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TimeScale
import noel.chronova.core.ui.R as CoreUiR
import noel.chronova.core.ui.TimeFormat
import noel.chronova.core.ui.assistant.AssistantSearchState
import noel.chronova.core.ui.assistant.AssistantSlot
import noel.chronova.core.ui.assistant.AssistantViewAction
import noel.chronova.core.ui.assistant.AssistantViewState
import noel.chronova.core.ui.assistant.ViewColor
import noel.chronova.core.ui.assistant.ViewKind
import noel.chronova.core.ui.assistant.ViewScale
import noel.chronova.core.ui.assistant.ViewZoom
import noel.chronova.core.ui.components.AuroraFab
import noel.chronova.core.ui.components.ChronovaTopBar
import noel.chronova.core.ui.components.ChronovaTopBarTitle
import noel.chronova.core.ui.components.FabColumn
import noel.chronova.feature.timeline.presentation.components.SchedulerTimeline
import noel.chronova.feature.timeline.presentation.components.TaskSearchBar
import noel.chronova.feature.timeline.presentation.components.TimelineControls

@Composable
fun ProjectTimelineScreen(
    viewModel: ProjectTimelineViewModel,
    assistant: AssistantSlot,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The clock for the now line and Today (tests fix it). */
    now: () -> Long = System::currentTimeMillis,
    /** Opens the import sheet for this project (the shell's); null hides the button. */
    onImportImage: (() -> Unit)? = null,
) {
    assistant.screenEffects()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val taskEditor by viewModel.taskEditor.collectAsStateWithLifecycle()
    val undoOffer by viewModel.undoOffer.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val pushOffer by viewModel.pushOffer.collectAsStateWithLifecycle()
    val pushBlocked by viewModel.pushBlocked.collectAsStateWithLifecycle()
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
                    onImportImage = onImportImage,
                    onSelectTask = viewModel::selectTask,
                    onCommitEdit = viewModel::commitEdit,
                    onTimeScale = viewModel::setTimeScale,
                    onZoomIn = viewModel::zoomIn,
                    onZoomOut = viewModel::zoomOut,
                    onZoomReset = viewModel::resetZoom,
                    onPinchZoom = viewModel::zoomBy,
                    onPinchEnd = viewModel::finishZoom,
                    onSnap = viewModel::setSnapMinutes,
                    onRowHeightStep = viewModel::changeRowHeight,
                    onCriticalPath = viewModel::setShowCriticalPath,
                    onAddTask = viewModel::startAddTask,
                    onEditTask = viewModel::startEditTask,
                    onMarkDone = viewModel::markSelectedDone,
                    onOpenSearch = viewModel::openSearch,
                    onCloseSearch = viewModel::closeSearch,
                    onSearchQuery = viewModel::onSearchQuery,
                    onToggleColorFilter = viewModel::toggleColorFilter,
                    onToggleKindFilter = viewModel::toggleKindFilter,
                    onClearSearch = viewModel::clearSearch,
                    onSearch = viewModel::searchFor,
                    onPush = viewModel::pushDependents,
                    onPushOfferShown = viewModel::dismissPushOffer,
                    onPushBlockedShown = viewModel::dismissPushBlocked,
                    onUndo = viewModel::undo,
                    onUndoShown = viewModel::dismissUndo,
                    onNoticeShown = viewModel::dismissNotice,
                ),
                assistant = assistant,
                undoOffer = undoOffer,
                notice = notice,
                pushOffer = pushOffer,
                pushBlocked = pushBlocked,
                now = now,
                modifier = modifier,
            )
            assistant.overlay(current.timeline.zone)
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
                    onMilestoneChange = viewModel::onTaskMilestoneChange,
                    onProgressChange = viewModel::onTaskProgressChange,
                    onDoneChange = viewModel::onTaskDoneChange,
                    onNotesChange = viewModel::onTaskNotesChange,
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
    val onCommitEdit: (original: TaskUi, edited: TaskUi) -> Unit = { _, _ -> },
    val onTimeScale: (TimeScale) -> Unit = {},
    val onZoomIn: () -> Unit = {},
    val onZoomOut: () -> Unit = {},
    val onZoomReset: () -> Unit = {},
    /** A pinch step on the grid (above 1 zooms in) and the end of the pinch. */
    val onPinchZoom: (Float) -> Unit = {},
    val onPinchEnd: () -> Unit = {},
    val onSnap: (Int) -> Unit = {},
    val onRowHeightStep: (Int) -> Unit = {},
    /** Shows or hides the critical path. */
    val onCriticalPath: (Boolean) -> Unit = {},
    val onAddTask: () -> Unit = {},
    val onEditTask: (String) -> Unit = {},
    /** Marks the selected task done. */
    val onMarkDone: () -> Unit = {},
    /** The search bar (open, close, type, toggle a chip, clear). */
    val onOpenSearch: () -> Unit = {},
    val onCloseSearch: () -> Unit = {},
    val onSearchQuery: (String) -> Unit = {},
    val onToggleColorFilter: (TaskColor) -> Unit = {},
    val onToggleKindFilter: (TaskKind) -> Unit = {},
    val onClearSearch: () -> Unit = {},
    /** The whole search at once: words and chips (the assistant's search). */
    val onSearch: (query: String, colors: Set<TaskColor>, kinds: Set<TaskKind>) -> Unit = { _, _, _ -> },
    /** Push the offered dependents; the offer or the "can't push" message was seen. */
    val onPush: () -> Unit = {},
    val onPushOfferShown: () -> Unit = {},
    val onPushBlockedShown: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onUndoShown: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
    /** Opens the import sheet (tasks from an image of a chart); null hides the button. */
    val onImportImage: (() -> Unit)? = null,
)

@Composable
internal fun TimelineContent(
    state: TimelineUiState,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
    undoOffer: UndoOffer? = null,
    notice: TimelineNotice? = null,
    assistant: AssistantSlot = AssistantSlot.None,
    /** The clock for the now line and Today (fixed in previews). */
    now: () -> Long = System::currentTimeMillis,
    pushOffer: PushOfferUi? = null,
    pushBlocked: PushBlockedUi? = null,
) {
    var showControls by rememberSaveable { mutableStateOf(true) }
    // Opening the search folds the timeline settings away, so the two don't crowd the screen.
    val searchOpen = state.search != null
    LaunchedEffect(searchOpen) { if (searchOpen) showControls = false }
    val snackbarHost = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.task_deleted)
    val markedDoneMessage = stringResource(R.string.task_marked_done)
    val undoLabel = stringResource(CoreUiR.string.undo)
    val resources = LocalResources.current
    LaunchedEffect(undoOffer) {
        val offer = undoOffer ?: return@LaunchedEffect
        val message = when (offer.kind) {
            UndoKind.TaskDeleted -> deletedMessage
            UndoKind.MarkedDone -> markedDoneMessage
            UndoKind.PushedDependents -> resources.getQuantityString(R.plurals.push_done, offer.count, offer.count)
        }
        val result = snackbarHost.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) actions.onUndo() else actions.onUndoShown()
    }
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
    val pushLabel = stringResource(R.string.push_action)
    LaunchedEffect(pushOffer) {
        val offer = pushOffer ?: return@LaunchedEffect
        val message = resources.getQuantityString(R.plurals.push_offer, offer.count, offer.taskTitle, offer.count)
        val result = snackbarHost.showSnackbar(message, actionLabel = pushLabel, withDismissAction = true, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) actions.onPush() else actions.onPushOfferShown()
    }
    LaunchedEffect(pushBlocked) {
        val blocked = pushBlocked ?: return@LaunchedEffect
        val titles = blocked.titles.joinToString(", ") { "“$it”" }
        val message = resources.getString(R.string.push_blocked, titles, TimeFormat.day(blocked.projectEndMillis, state.zone))
        snackbarHost.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long)
        actions.onPushBlockedShown()
    }
    // The assistant is told what is shown, so it understands "this task" or "zoom in".
    val shownView = AssistantViewState(
        scale = ViewScale.valueOf(state.config.timeScale.name),
        criticalPath = state.config.showCriticalPath,
        selectedTaskId = state.selectedTaskId,
        search = state.search?.filter?.takeIf { it.isActive }?.let { filter ->
            AssistantSearchState(
                query = filter.query.trim().ifEmpty { null },
                colors = filter.colors.mapTo(mutableSetOf()) { ViewColor.valueOf(it.name) },
                kinds = filter.kinds.mapTo(mutableSetOf()) { ViewKind.valueOf(it.name) },
            )
        },
    )
    val reportView by rememberUpdatedState(assistant.reportView)
    LaunchedEffect(shownView) { reportView(shownView) }
    // The assistant asked for a view setting (scale, zoom, rows, critical path), a search or a task's editor:
    // as if the settings row, the search bar or the task's name had been used.
    val viewSetting = assistant.viewRequest?.takeIf { it.action.isViewSetting() }
    val currentActions by rememberUpdatedState(actions)
    LaunchedEffect(viewSetting?.key) {
        val request = viewSetting ?: return@LaunchedEffect
        when (val action = request.action) {
            is AssistantViewAction.SetScale -> currentActions.onTimeScale(TimeScale.valueOf(action.scale.name))
            is AssistantViewAction.Zoom -> when (action.step) {
                ViewZoom.IN -> currentActions.onZoomIn()
                ViewZoom.OUT -> currentActions.onZoomOut()
                ViewZoom.RESET -> currentActions.onZoomReset()
            }
            is AssistantViewAction.RowHeight -> currentActions.onRowHeightStep(if (action.bigger) 1 else -1)
            is AssistantViewAction.CriticalPath -> currentActions.onCriticalPath(action.on)
            is AssistantViewAction.Search -> currentActions.onSearch(
                action.query.orEmpty(),
                action.colors.mapTo(mutableSetOf()) { TaskColor.valueOf(it.name) },
                action.kinds.mapTo(mutableSetOf()) { TaskKind.valueOf(it.name) },
            )
            AssistantViewAction.ClearSearch -> currentActions.onCloseSearch()
            is AssistantViewAction.OpenTask -> currentActions.onEditTask(action.taskId)
            else -> Unit
        }
        request.onHandled()
    }
    val scope = rememberCoroutineScope()
    val todayBefore = stringResource(R.string.today_before)
    val todayAfter = stringResource(R.string.today_after)
    val onTodayOutside: (Boolean, Long) -> Unit = { startsLater, edge ->
        val day = TimeFormat.day(edge, state.zone)
        scope.launch { snackbarHost.showSnackbar((if (startsLater) todayBefore else todayAfter).format(day), duration = SnackbarDuration.Short) }
    }
    val editNotSavedMessage = stringResource(R.string.edit_not_saved)
    val undoNotPossibleMessage = stringResource(CoreUiR.string.undo_not_possible)
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
                selectedIsDone = state.tasks.firstOrNull { it.id == state.selectedTaskId }?.isDone ?: true,
                searchOpen = state.search != null,
                onSearch = if (state.search != null) actions.onCloseSearch else actions.onOpenSearch,
                onBack = actions.onBack,
                onEditTask = actions.onEditTask,
                onMarkDone = actions.onMarkDone,
                onToggleControls = { showControls = !showControls },
                onImportImage = actions.onImportImage,
            )
        },
        floatingActionButton = {
            FabColumn {
                // The assistant's orb, just above "Add task" where it's easy to find.
                if (assistant.available) assistant.orbButton(Modifier)
                AuroraFab(stringResource(R.string.task_add), onClick = actions.onAddTask) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            state.search?.let { search ->
                TaskSearchBar(
                    search = search,
                    onQuery = actions.onSearchQuery,
                    onToggleColor = actions.onToggleColorFilter,
                    onToggleKind = actions.onToggleKindFilter,
                    onClear = actions.onClearSearch,
                    onClose = actions.onCloseSearch,
                )
            }
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
                        onCriticalPath = actions.onCriticalPath,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SchedulerTimeline(
                state = state,
                onSelectTask = actions.onSelectTask,
                onCommitEdit = actions.onCommitEdit,
                onEditTask = actions.onEditTask,
                highlighted = assistant.highlighted,
                onZoomBy = actions.onPinchZoom,
                onZoomEnd = actions.onPinchEnd,
                modifier = Modifier.weight(1f),
                // Lets the last rows scroll clear of the FAB.
                contentPadding = PaddingValues(bottom = 88.dp),
                now = now,
                onTodayOutside = onTodayOutside,
                // Settings changes are done above; the grid only scrolls and selects.
                viewRequest = assistant.viewRequest?.takeUnless { it.action.isViewSetting() },
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
    selectedIsDone: Boolean,
    searchOpen: Boolean,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onEditTask: (String) -> Unit,
    onMarkDone: () -> Unit,
    onToggleControls: () -> Unit,
    onImportImage: (() -> Unit)? = null,
) {
    ChronovaTopBar(
        title = { ChronovaTopBarTitle(title) },
        onBack = onBack,
        backDescription = stringResource(R.string.timeline_back),
        actions = {
            if (selectedTaskId != null && !selectedIsDone) {
                IconButton(onClick = onMarkDone) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.task_mark_done))
                }
            }
            if (selectedTaskId != null) {
                IconButton(onClick = { onEditTask(selectedTaskId) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.task_edit_selected))
                }
            }
            // Only while nothing is selected, so a 360 dp bar never holds more than four buttons.
            if (onImportImage != null && selectedTaskId == null) {
                IconButton(onClick = onImportImage) {
                    Icon(painterResource(R.drawable.ic_add_photo), contentDescription = stringResource(R.string.timeline_import_image))
                }
            }
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(if (searchOpen) R.string.search_close else R.string.search_open),
                    tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleControls) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(if (controlsVisible) R.string.timeline_settings_hide else R.string.timeline_settings_show),
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

/** Done by the screen (how the timeline is shown, the search, the editor), rather than by scrolling the grid. */
private fun AssistantViewAction.isViewSetting(): Boolean = when (this) {
    is AssistantViewAction.SetScale, is AssistantViewAction.Zoom, is AssistantViewAction.RowHeight, is AssistantViewAction.CriticalPath,
    is AssistantViewAction.Search, AssistantViewAction.ClearSearch, is AssistantViewAction.OpenTask,
    -> true
    AssistantViewAction.GoToToday, is AssistantViewAction.GoToDate, is AssistantViewAction.ShowTask -> false
}
