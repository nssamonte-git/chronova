package noel.chronova.app.presentation.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import noel.chronova.app.domain.model.ProjectSummary
import noel.chronova.app.domain.repository.ProjectRepository

sealed interface ProjectListState {
    data object Loading : ProjectListState
    data class Loaded(val projects: List<ProjectSummary>) : ProjectListState
}

class ProjectListViewModel(repository: ProjectRepository) : ViewModel() {

    val state: StateFlow<ProjectListState> = repository.observeProjects()
        .map<List<ProjectSummary>, ProjectListState> { ProjectListState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectListState.Loading)

    companion object {
        fun factory(repository: ProjectRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProjectListViewModel(repository) }
        }
    }
}
