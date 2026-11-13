package noel.chronova.app.navigation

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
    NavHost(navController = navController, startDestination = ProjectListRoute, modifier = modifier) {
        composable<ProjectListRoute> {
            // The project being exported (its sheet); kept across rotation.
            var exportId by rememberSaveable { mutableStateOf<String?>(null) }
            var exportName by rememberSaveable { mutableStateOf("") }
            ProjectListScreen(
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
            )
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
            ProjectTimelineScreen(
                viewModel = viewModel(factory = ProjectTimelineViewModel.factory(route.projectId, container.timeline::useCases)),
                // The timeline knows the assistant only through this slot.
                assistant = rememberAssistantSlot(assistant),
                onBack = { navController.popBackStack(ProjectListRoute, inclusive = false) },
            )
        }
    }
}
