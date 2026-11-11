package noel.chronova.app.navigation

import androidx.compose.runtime.Composable
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
import noel.chronova.app.presentation.timeline.ProjectTimelineScreen
import noel.chronova.app.presentation.timeline.ProjectTimelineViewModel

@Serializable
data object ProjectListRoute

@Serializable
data class ProjectTimelineRoute(val projectId: String)

@Serializable
data object AccountRoute

@Composable
fun ChronovaNavHost(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ProjectListRoute, modifier = modifier) {
        composable<ProjectListRoute> {
            ProjectListScreen(
                viewModel = viewModel(factory = ProjectListViewModel.factory(container.projectRepository)),
                onOpenProject = { id -> navController.navigate(ProjectTimelineRoute(id)) },
                onOpenAccount = { navController.navigate(AccountRoute) },
            )
        }
        composable<AccountRoute> {
            AccountScreen(
                viewModel = viewModel(factory = AccountViewModel.factory(container.authRepository)),
                onBack = { navController.popBackStack() },
            )
        }
        composable<ProjectTimelineRoute> {
            ProjectTimelineScreen(
                viewModel = viewModel(factory = ProjectTimelineViewModel.factory(container.projectRepository)),
                onBack = { navController.popBackStack(ProjectListRoute, inclusive = false) },
            )
        }
    }
}
