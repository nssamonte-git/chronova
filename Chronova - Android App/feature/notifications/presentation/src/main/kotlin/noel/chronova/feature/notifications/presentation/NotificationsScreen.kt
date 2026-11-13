package noel.chronova.feature.notifications.presentation

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import noel.chronova.core.designsystem.ChronovaSpacing
import noel.chronova.core.designsystem.R as DesignR
import noel.chronova.core.designsystem.numeric
import noel.chronova.core.domain.notifications.NotificationCategory
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.ui.components.ChronovaTopBar
import noel.chronova.core.ui.components.ChronovaTopBarTitle
import noel.chronova.core.ui.components.EmptyState
import noel.chronova.core.ui.components.SyncIcon
import noel.chronova.core.ui.components.horizontalFadingEdges

/** Everything that happened (sync, assistant, account), with filters and sorting. */
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit, onOpenProject: (String) -> Unit) {
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val isEmpty by viewModel.isEmpty.collectAsStateWithLifecycle()
    val choices by viewModel.projectChoices.collectAsStateWithLifecycle()
    val live by viewModel.liveProjectIds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Android 13+: important notifications also reach the shade once allowed; asked once, here.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !asked) {
            asked = true
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val undone = stringResource(R.string.notifications_undone)
    val cannotUndo = stringResource(R.string.notifications_cannot_undo)
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(if (it == NotificationsMessage.UNDONE) undone else cannotUndo)
            viewModel.dismissMessage()
        }
    }

    NotificationsContent(
        page = NotificationsPageUi(shown, filter, isEmpty, choices, live),
        actions = remember(viewModel, onBack, onOpenProject) {
            NotificationsActions(
                onBack = onBack,
                onOpenProject = onOpenProject,
                onMarkRead = viewModel::markRead,
                onUndo = viewModel::undo,
                onMarkAllRead = viewModel::markAllRead,
                onClear = viewModel::clear,
                onSetCategory = viewModel::setCategory,
                onToggleUnreadOnly = viewModel::toggleUnreadOnly,
                onSetProject = viewModel::setProject,
                onToggleOrder = viewModel::toggleOrder,
            )
        },
        snackbar = snackbar,
    )
}

/** The notifications page without its ViewModel: what it shows, and what each control does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsContent(
    page: NotificationsPageUi,
    actions: NotificationsActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    /** The time "5 minutes ago" is counted from; fixed in screenshots. */
    now: () -> Long = System::currentTimeMillis,
) {
    val resources = LocalResources.current
    Scaffold(
        topBar = {
            ChronovaTopBar(
                title = { ChronovaTopBarTitle(stringResource(R.string.notifications_title)) },
                onBack = actions.onBack,
                actions = {
                    var open by remember { mutableStateOf(false) }
                    IconButton(onClick = { open = true }, enabled = !page.isEmpty) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.notifications_more))
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notifications_mark_all_read)) },
                            onClick = { open = false; actions.onMarkAllRead() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notifications_clear)) },
                            onClick = { open = false; actions.onClear() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Filters(page.filter.category, page.filter.unreadOnly, page.filter.projectId, page.filter.newestFirst, page.choices, actions)
            HorizontalDivider()
            if (page.shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(title = stringResource(if (page.isEmpty) R.string.notifications_empty else R.string.notifications_empty_filtered))
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(page.shown, key = { it.id }) { notification ->
                        Item(
                            notification,
                            text = notificationText(notification, resources),
                            canOpen = notification.projectId != null && notification.projectId in page.liveProjectIds,
                            now = now(),
                            onClick = { actions.onMarkRead(notification.id) },
                            onUndo = { actions.onUndo(notification) },
                            onOpen = {
                                actions.onMarkRead(notification.id)
                                notification.projectId?.let(actions.onOpenProject)
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(
    category: NotificationCategory?,
    unreadOnly: Boolean,
    projectId: String?,
    newestFirst: Boolean,
    choices: List<ProjectChoice>,
    actions: NotificationsActions,
) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            // More filters than fit: the edges fade while there is more to scroll to.
            .horizontalFadingEdges(scroll)
            .horizontalScroll(scroll)
            .padding(horizontal = ChronovaSpacing.screenGutter, vertical = ChronovaSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val categories = listOf(
            null to R.string.notifications_all,
            NotificationCategory.SYNC to R.string.notifications_sync,
            NotificationCategory.ASSISTANT to R.string.notifications_assistant,
            NotificationCategory.ACCOUNT to R.string.notifications_account,
        )
        categories.forEach { (value, label) ->
            FilterChip(selected = category == value, onClick = { actions.onSetCategory(value) }, label = { Text(stringResource(label)) })
        }
        FilterChip(selected = unreadOnly, onClick = actions.onToggleUnreadOnly, label = { Text(stringResource(R.string.notifications_unread_only)) })
        Box {
            var open by remember { mutableStateOf(false) }
            FilterChip(
                selected = projectId != null,
                onClick = { open = true },
                enabled = choices.isNotEmpty(),
                label = { Text(choices.firstOrNull { it.id == projectId }?.name ?: stringResource(R.string.notifications_all_projects)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notifications_all_projects)) },
                    onClick = { open = false; actions.onSetProject(null) },
                )
                choices.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice.name) }, onClick = { open = false; actions.onSetProject(choice.id) })
                }
            }
        }
        FilterChip(
            selected = !newestFirst,
            onClick = actions.onToggleOrder,
            label = { Text(stringResource(if (newestFirst) R.string.notifications_newest_first else R.string.notifications_oldest_first)) },
        )
    }
}

@Composable
private fun Item(
    notification: NotificationUi,
    text: NotificationText,
    canOpen: Boolean,
    now: Long,
    onClick: () -> Unit,
    onUndo: () -> Unit,
    onOpen: () -> Unit,
) {
    val unread = stringResource(R.string.notifications_unread)
    val rail = MaterialTheme.colorScheme.primary
    // Read: the row settles back into the page and its rail fades, rather than jumping.
    val background by animateColorAsState(
        if (notification.read) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainer,
        label = "notificationRow",
    )
    val railAlpha by animateFloatAsState(if (notification.read) 0f else 1f, label = "notificationRail")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // Unread: a raised row with a rail at its start, rather than a wash of the accent colour.
            .background(background)
            .drawBehind { if (railAlpha > 0f) drawRect(rail.copy(alpha = railAlpha), size = Size(3.dp.toPx(), size.height)) }
            .padding(horizontal = ChronovaSpacing.screenGutter, vertical = ChronovaSpacing.m)
            .semantics(mergeDescendants = true) { if (!notification.read) contentDescription = "$unread. ${text.title}. ${text.body}" },
        verticalAlignment = Alignment.Top,
    ) {
        CategoryIcon(notification.kind)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.read) FontWeight.Normal else FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!notification.read) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            Text(text.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val time = if (now - notification.createdAt < DateUtils.MINUTE_IN_MILLIS) {
                stringResource(R.string.notifications_just_now)
            } else {
                DateUtils.getRelativeTimeSpanString(notification.createdAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
            }
            Text(
                listOfNotNull(notification.projectName, time).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.numeric(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (notification.canUndo || canOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (notification.canUndo) TextButton(onClick = onUndo) { Text(stringResource(R.string.notifications_undo)) }
                    if (canOpen) TextButton(onClick = onOpen) { Text(stringResource(R.string.notifications_open_project)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryIcon(kind: NotificationKind) {
    val colors = MaterialTheme.colorScheme
    // The icon sits in a tinted circle of its own colour: red for something to act on, the accent for the assistant.
    val (container, tint) = when {
        kind.important -> colors.errorContainer to colors.onErrorContainer
        kind.category == NotificationCategory.ASSISTANT -> colors.primaryContainer to colors.onPrimaryContainer
        else -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    Box(Modifier.size(40.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
        val icon = Modifier.size(20.dp)
        when (kind.category) {
            NotificationCategory.SYNC -> Icon(SyncIcon, contentDescription = null, tint = tint, modifier = icon)
            NotificationCategory.ASSISTANT -> Icon(painterResource(DesignR.drawable.ic_mic), contentDescription = null, tint = tint, modifier = icon)
            NotificationCategory.ACCOUNT -> Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = tint, modifier = icon)
        }
    }
}
