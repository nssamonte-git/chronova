package noel.chronova.feature.notifications.presentation

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import noel.chronova.core.domain.notifications.AppNotification
import noel.chronova.core.domain.notifications.NotificationCategory
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.designsystem.R as DesignR
import noel.chronova.core.ui.R as CoreUiR

/** Everything that happened (sync, assistant, account), with filters and sorting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit, onOpenProject: (String) -> Unit) {
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val isEmpty by viewModel.isEmpty.collectAsStateWithLifecycle()
    val choices by viewModel.projectChoices.collectAsStateWithLifecycle()
    val live by viewModel.liveProjectIds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.back))
                    }
                },
                actions = {
                    var open by remember { mutableStateOf(false) }
                    IconButton(onClick = { open = true }, enabled = !isEmpty) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.notifications_more))
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notifications_mark_all_read)) },
                            onClick = { open = false; viewModel.markAllRead() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notifications_clear)) },
                            onClick = { open = false; viewModel.clear() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Filters(filter.category, filter.unreadOnly, filter.projectId, filter.newestFirst, choices, viewModel)
            HorizontalDivider()
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (isEmpty) R.string.notifications_empty else R.string.notifications_empty_filtered),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(shown, key = { it.id }) { notification ->
                        Item(
                            notification,
                            text = notificationText(notification, resources),
                            canOpen = notification.projectId != null && notification.projectId in live,
                            onClick = { viewModel.markRead(notification.id) },
                            onUndo = { viewModel.undo(notification) },
                            onOpen = {
                                viewModel.markRead(notification.id)
                                notification.projectId?.let(onOpenProject)
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
    viewModel: NotificationsViewModel,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            FilterChip(selected = category == value, onClick = { viewModel.setCategory(value) }, label = { Text(stringResource(label)) })
        }
        FilterChip(selected = unreadOnly, onClick = viewModel::toggleUnreadOnly, label = { Text(stringResource(R.string.notifications_unread_only)) })
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
                    onClick = { open = false; viewModel.setProject(null) },
                )
                choices.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice.name) }, onClick = { open = false; viewModel.setProject(choice.id) })
                }
            }
        }
        FilterChip(
            selected = !newestFirst,
            onClick = viewModel::toggleOrder,
            label = { Text(stringResource(if (newestFirst) R.string.notifications_newest_first else R.string.notifications_oldest_first)) },
        )
    }
}

@Composable
private fun Item(
    notification: AppNotification,
    text: NotificationText,
    canOpen: Boolean,
    onClick: () -> Unit,
    onUndo: () -> Unit,
    onOpen: () -> Unit,
) {
    val unread = stringResource(R.string.notifications_unread)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (notification.read) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
            val now = System.currentTimeMillis()
            val time = if (now - notification.createdAt < DateUtils.MINUTE_IN_MILLIS) {
                stringResource(R.string.notifications_just_now)
            } else {
                DateUtils.getRelativeTimeSpanString(notification.createdAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
            }
            Text(
                listOfNotNull(notification.projectName, time).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
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
    val tint = when {
        kind.important -> MaterialTheme.colorScheme.error
        kind.category == NotificationCategory.ASSISTANT -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.padding(top = 2.dp).size(24.dp), contentAlignment = Alignment.Center) {
        when (kind.category) {
            NotificationCategory.SYNC -> Icon(Icons.Filled.Refresh, contentDescription = null, tint = tint)
            NotificationCategory.ASSISTANT -> Icon(painterResource(DesignR.drawable.ic_mic), contentDescription = null, tint = tint)
            NotificationCategory.ACCOUNT -> Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = tint)
        }
    }
}
