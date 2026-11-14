package noel.chronova.app.navigation

import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import noel.chronova.feature.chartimport.presentation.ImportScreen
import noel.chronova.feature.chartimport.presentation.ImportViewModel
import noel.chronova.feature.chartimport.presentation.R as ImportR
import noel.chronova.feature.export.domain.ExportFormat
import noel.chronova.core.model.ReminderLead
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.Manifest
import noel.chronova.feature.assistant.presentation.AppRequestUi
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import noel.chronova.feature.export.presentation.ExportScreen
import noel.chronova.feature.export.presentation.ExportViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import noel.chronova.app.AppContainer
import noel.chronova.feature.account.presentation.AccountScreen
import noel.chronova.feature.account.presentation.AccountViewModel
import noel.chronova.feature.notifications.presentation.NotificationsScreen
import noel.chronova.feature.notifications.presentation.NotificationsViewModel
import noel.chronova.feature.projects.presentation.ProjectListScreen
import noel.chronova.feature.projects.presentation.ProjectListViewModel
import noel.chronova.feature.sync.presentation.SyncPromptDialog
import noel.chronova.feature.sync.presentation.SyncViewModel
import noel.chronova.feature.timeline.presentation.ProjectTimelineScreen
import noel.chronova.feature.timeline.presentation.ProjectTimelineViewModel
import noel.chronova.feature.assistant.presentation.AssistantViewModel
import noel.chronova.feature.assistant.presentation.rememberAssistantSlot

@Serializable
data object ProjectListRoute

@Serializable
data class ProjectTimelineRoute(val projectId: String)

@Serializable
data object AccountRoute

@Serializable
data object NotificationsRoute

/** [openNotifications]: a tapped system notification asked for the notifications page; [onOpened] once shown. */
@Composable
fun ChronovaNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    openNotifications: Boolean = false,
    onOpened: () -> Unit = {},
    /** A tapped task reminder asked for this project; [onProjectOpened] once shown. */
    openProject: String? = null,
    onProjectOpened: () -> Unit = {},
) {
    val auth by container.account.auth.state.collectAsStateWithLifecycle()
    when (AppGate.of(auth)) {
        AppGate.Loading -> Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        // Signing out (or a session the server ended) lands here, and the app's screens start afresh after signing in.
        AppGate.SignIn -> AccountScreen(
            viewModel = viewModel(key = "sign-in", factory = AccountViewModel.factory(container.account.auth, container.account.syncStatus, container.account.appearance, container.account.reminders, container.account::useCases)),
            onBack = null,
        )
        AppGate.App -> ChronovaScreens(container, modifier, openNotifications, onOpened, openProject, onProjectOpened)
    }
}

@Composable
private fun ChronovaScreens(
    container: AppContainer,
    modifier: Modifier,
    openNotifications: Boolean,
    onOpened: () -> Unit,
    openProject: String?,
    onProjectOpened: () -> Unit,
) {
    val navController = rememberNavController()
    val unread by container.notifications.queries.unreadCount().collectAsStateWithLifecycle(initialValue = 0)
    LaunchedEffect(openProject) {
        if (openProject != null) {
            navController.navigate(ProjectTimelineRoute(openProject)) { launchSingleTop = true }
            onProjectOpened()
        }
    }
    LaunchedEffect(openNotifications) {
        if (openNotifications) {
            navController.navigate(NotificationsRoute) { launchSingleTop = true }
            onOpened()
        }
    }
    // Activity-scoped: the reconnect prompt can appear over any screen.
    val sync: SyncViewModel = viewModel(factory = SyncViewModel.factory(container.sync.prompts, container.sync::useCases))
    val syncUi by sync.ui.collectAsStateWithLifecycle()
    val prompt by sync.prompt.collectAsStateWithLifecycle()
    prompt?.let {
        SyncPromptDialog(
            prompt = it,
            onSync = sync::syncNow,
            onSignIn = {
                sync.dismissPrompt()
                navController.navigate(AccountRoute)
            },
            onLater = sync::dismissPrompt,
        )
    }
    // What the assistant asked the app to do: go elsewhere, a setting, sync, an export.
    var voiceExport by remember { mutableStateOf<AppRequestUi.Export?>(null) }
    val context = LocalContext.current
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val doRequest: (AppRequestUi) -> Unit = { request ->
        when (request) {
            is AppRequestUi.OpenProject -> navController.navigate(ProjectTimelineRoute(request.projectId)) { launchSingleTop = true }
            is AppRequestUi.Projects -> navController.popBackStack(ProjectListRoute, inclusive = false)
            is AppRequestUi.Notifications -> navController.navigate(NotificationsRoute) { launchSingleTop = true }
            is AppRequestUi.Account -> navController.navigate(AccountRoute) { launchSingleTop = true }
            is AppRequestUi.Theme -> container.account.useCases().setThemeMode(request.theme)
            is AppRequestUi.Reminders -> {
                container.account.useCases().setReminderLead(request.lead)
                // As on the account page: reminders need the notification permission (Android 13+).
                val allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (request.lead != ReminderLead.OFF && !allowed && Build.VERSION.SDK_INT >= 33) {
                    askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            is AppRequestUi.Sync -> sync.syncNow()
            is AppRequestUi.Export -> voiceExport = request
        }
    }
    voiceExport?.let { request ->
        val export: ExportViewModel = viewModel(
            key = "voice-export-${request.id}",
            factory = ExportViewModel.factory(request.projectId, request.projectName, container.export::exportProject),
        )
        // The sheet shows what is being exported; the share sheet opens once the file is written.
        LaunchedEffect(request.id) {
            export.chooseFormat(ExportFormat.valueOf(request.format))
            export.setIncludeNotes(request.notes)
            export.export()
        }
        ExportScreen(viewModel = export, onDismiss = { voiceExport = null })
    }
    NavHost(navController = navController, startDestination = ProjectListRoute, modifier = modifier) {
        composable<ProjectListRoute> {
            // The project being exported (its sheet); kept across rotation.
            var exportId by rememberSaveable { mutableStateOf<String?>(null) }
            // An open import sheet (a new project from an image), numbered so each opening starts afresh:
            // the count only goes up, so a closed sheet's number (and its ViewModel) is never reused.
            var importing by rememberSaveable { mutableStateOf<Int?>(null) }
            var importOpenings by rememberSaveable { mutableIntStateOf(0) }
            var exportName by rememberSaveable { mutableStateOf("") }
            // The assistant on the list of projects: where to go (no project is open here).
            val assistant: AssistantViewModel = viewModel(
                key = "assistant-projects",
                factory = AssistantViewModel.factory(null, container.assistant::useCases, container.assistant.phrases),
            )
            AssistantRequests(assistant, doRequest)
            ProjectListScreen(
                assistant = rememberAssistantSlot(assistant),
                viewModel = viewModel(factory = ProjectListViewModel.factory(container.projects.queries, container.projects::useCases)),
                onOpenProject = { id -> navController.navigate(ProjectTimelineRoute(id)) },
                onOpenAccount = { navController.navigate(AccountRoute) },
                syncUi = syncUi,
                onSync = sync::syncNow,
                unreadNotifications = unread,
                onOpenNotifications = { navController.navigate(NotificationsRoute) },
                onExport = { id, name ->
                    exportId = id
                    exportName = name
                },
                onImportImage = { importing = ++importOpenings },
            )
            importing?.let { session ->
                ImportSheetHost(container, projectId = null, session = session, onDismiss = { importing = null }) { id ->
                    navController.navigate(ProjectTimelineRoute(id))
                }
            }
            exportId?.let { id ->
                ExportScreen(
                    viewModel = viewModel(key = "export-$id", factory = ExportViewModel.factory(id, exportName, container.export::exportProject)),
                    onDismiss = { exportId = null },
                )
            }
        }
        composable<NotificationsRoute> {
            NotificationsScreen(
                viewModel = viewModel(factory = NotificationsViewModel.factory(container.notifications.queries, container.projects.queries, container.notifications::useCases)),
                onBack = { navController.popBackStack() },
                onOpenProject = { id -> navController.navigate(ProjectTimelineRoute(id)) },
            )
        }
        composable<AccountRoute> {
            AccountScreen(
                viewModel = viewModel(factory = AccountViewModel.factory(container.account.auth, container.account.syncStatus, container.account.appearance, container.account.reminders, container.account::useCases)),
                onBack = { navController.popBackStack() },
            )
        }
        composable<ProjectTimelineRoute> { entry ->
            val route = entry.toRoute<ProjectTimelineRoute>()
            val assistant: AssistantViewModel = viewModel(
                factory = AssistantViewModel.factory(route.projectId, container.assistant::useCases, container.assistant.phrases),
            )
            AssistantRequests(assistant, doRequest)
            // As on the list: each opening of the import sheet gets a number never used before.
            var importing by rememberSaveable { mutableStateOf<Int?>(null) }
            var importOpenings by rememberSaveable { mutableIntStateOf(0) }
            ProjectTimelineScreen(
                viewModel = viewModel(factory = ProjectTimelineViewModel.factory(route.projectId, container.timeline::useCases)),
                // The timeline knows the assistant only through this slot.
                assistant = rememberAssistantSlot(assistant),
                onBack = { navController.popBackStack(ProjectListRoute, inclusive = false) },
                onImportImage = { importing = ++importOpenings },
            )
            importing?.let { session ->
                ImportSheetHost(container, projectId = route.projectId, session = session, onDismiss = { importing = null }) {}
            }
        }
    }
}

/**
 * The import sheet (tasks from an image of a chart) for a new project ([projectId] null) or an open
 * one. Each opening ([session]) gets its own ViewModel, so the sheet never reopens on an old import.
 */
@Composable
private fun ImportSheetHost(container: AppContainer, projectId: String?, session: Int, onDismiss: () -> Unit, onOpenProject: (String) -> Unit) {
    val untitled = stringResource(ImportR.string.import_untitled_project)
    ImportScreen(
        viewModel = viewModel(
            key = "import-${projectId ?: "new"}-$session",
            factory = ImportViewModel.factory(projectId, container.chartImport::useCases, { LocalDate.now() }, untitled),
        ),
        onDismiss = onDismiss,
        onOpenProject = onOpenProject,
    )
}

/** Does what the assistant asked the app to do (it has already said so), once per request, in order. */
@Composable
private fun AssistantRequests(assistant: AssistantViewModel, doRequest: (AppRequestUi) -> Unit) {
    val request by assistant.appRequest.collectAsStateWithLifecycle()
    val currentDo by rememberUpdatedState(doRequest)
    LaunchedEffect(request?.id) {
        val asked = request ?: return@LaunchedEffect
        assistant.appRequestDone(asked.id)
        currentDo(asked)
    }
}
