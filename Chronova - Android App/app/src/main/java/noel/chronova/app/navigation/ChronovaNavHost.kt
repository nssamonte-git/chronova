package noel.chronova.app.navigation

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
import noel.chronova.app.presentation.account.AccountScreen
import noel.chronova.app.presentation.account.AccountViewModel
import noel.chronova.app.presentation.notifications.NotificationsScreen
import noel.chronova.app.presentation.notifications.NotificationsViewModel
import noel.chronova.app.presentation.projects.ProjectListScreen
import noel.chronova.app.presentation.projects.ProjectListViewModel
import noel.chronova.app.presentation.sync.SyncPromptDialog
import noel.chronova.app.presentation.sync.SyncViewModel
import noel.chronova.app.presentation.timeline.ProjectTimelineScreen
import noel.chronova.app.presentation.timeline.ProjectTimelineViewModel
import noel.chronova.app.presentation.voice.AssistantViewModel
import noel.chronova.app.presentation.voice.rememberAssistantSlot

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
) {
    val auth by container.authRepository.state.collectAsStateWithLifecycle()
    when (AppGate.of(auth)) {
        AppGate.Loading -> Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        // Signing out (or a session the server ended) lands here, and the app's screens start afresh after signing in.
        AppGate.SignIn -> AccountScreen(
            viewModel = viewModel(key = "sign-in", factory = AccountViewModel.factory(container::accountUseCases)),
            onBack = null,
        )
        AppGate.App -> ChronovaScreens(container, modifier, openNotifications, onOpened)
    }
}

@Composable
private fun ChronovaScreens(container: AppContainer, modifier: Modifier, openNotifications: Boolean, onOpened: () -> Unit) {
    val navController = rememberNavController()
    val unread by container.observeUnreadNotificationCount().collectAsStateWithLifecycle(initialValue = 0)
    LaunchedEffect(openNotifications) {
        if (openNotifications) {
            navController.navigate(NotificationsRoute) { launchSingleTop = true }
            onOpened()
        }
    }
    // Activity-scoped: the reconnect prompt can appear over any screen.
    val sync: SyncViewModel = viewModel(factory = SyncViewModel.factory(container::syncUseCases))
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
            ProjectListScreen(
                viewModel = viewModel(factory = ProjectListViewModel.factory(container::projectUseCases)),
                onOpenProject = { id -> navController.navigate(ProjectTimelineRoute(id)) },
                onOpenAccount = { navController.navigate(AccountRoute) },
                syncUi = syncUi,
                onSync = sync::syncNow,
                unreadNotifications = unread,
                onOpenNotifications = { navController.navigate(NotificationsRoute) },
            )
        }
        composable<NotificationsRoute> {
            NotificationsScreen(
                viewModel = viewModel(factory = NotificationsViewModel.factory(container::notificationUseCases)),
                onBack = { navController.popBackStack() },
                onOpenProject = { id -> navController.navigate(ProjectTimelineRoute(id)) },
            )
        }
        composable<AccountRoute> {
            AccountScreen(
                viewModel = viewModel(factory = AccountViewModel.factory(container::accountUseCases)),
                onBack = { navController.popBackStack() },
            )
        }
        composable<ProjectTimelineRoute> { entry ->
            val route = entry.toRoute<ProjectTimelineRoute>()
            val assistant: AssistantViewModel = viewModel(
                factory = AssistantViewModel.factory(route.projectId, container::assistantUseCases, container.assistantPhrases),
            )
            ProjectTimelineScreen(
                viewModel = viewModel(factory = ProjectTimelineViewModel.factory(route.projectId, container::timelineUseCases)),
                // The timeline knows the assistant only through this slot.
                assistant = rememberAssistantSlot(assistant),
                onBack = { navController.popBackStack(ProjectListRoute, inclusive = false) },
            )
        }
    }
}
