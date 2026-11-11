package noel.chronova.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import noel.chronova.app.AppContainer
import noel.chronova.app.presentation.account.AccountScreen
import noel.chronova.app.presentation.account.AccountViewModel
import noel.chronova.app.presentation.projects.ProjectListScreen
import noel.chronova.app.presentation.projects.ProjectListViewModel
import noel.chronova.app.presentation.sync.SyncPromptDialog
import noel.chronova.app.presentation.sync.SyncViewModel
import noel.chronova.app.presentation.timeline.ProjectTimelineScreen
import noel.chronova.app.presentation.timeline.ProjectTimelineViewModel
import noel.chronova.app.presentation.voice.AssistantViewModel

@Serializable
data object ProjectListRoute

@Serializable
data class ProjectTimelineRoute(val projectId: String)

@Serializable
data object AccountRoute

@Composable
fun ChronovaNavHost(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    // Activity-scoped: the reconnect prompt can appear over any screen.
    val sync: SyncViewModel = viewModel(factory = SyncViewModel.factory(container.syncCoordinator, container.authRepository))
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
                viewModel = viewModel(factory = ProjectListViewModel.factory(container.projectRepository)),
                onOpenProject = { id -> navController.navigate(ProjectTimelineRoute(id)) },
                onOpenAccount = { navController.navigate(AccountRoute) },
                syncUi = syncUi,
                onSync = sync::syncNow,
            )
        }
        composable<AccountRoute> {
            AccountScreen(
                viewModel = viewModel(factory = AccountViewModel.factory(container.authRepository, container.syncCoordinator)),
                onBack = { navController.popBackStack() },
            )
        }
        composable<ProjectTimelineRoute> {
            ProjectTimelineScreen(
                viewModel = viewModel(factory = ProjectTimelineViewModel.factory(container.projectRepository)),
                assistant = viewModel(
                    factory = AssistantViewModel.factory(container.projectRepository, container.voiceApi, container.authRepository, container.online, container::newVoiceRecorder),
                ),
                onBack = { navController.popBackStack(ProjectListRoute, inclusive = false) },
            )
        }
    }
}
