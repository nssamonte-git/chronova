package noel.chronova.feature.projects.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import noel.chronova.core.model.ProjectSummary
import noel.chronova.feature.projects.domain.ProjectUseCases
import noel.chronova.feature.projects.domain.SaveProjectResult
import noel.chronova.core.domain.repository.ProjectQueries

sealed interface ProjectListState {
    data object Loading : ProjectListState
    data class Loaded(val projects: List<ProjectRowUi>) : ProjectListState
}

/** The create/edit sheet. [projectId] is null when creating. [problem] shows after a failed save. */
data class ProjectEditorState(
    val projectId: String?,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val problem: ProjectProblemUi? = null,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = projectId == null
}

class ProjectListViewModel(
    projects: ProjectQueries,
    private val useCases: ProjectUseCases,
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
) : ViewModel() {

    val state: StateFlow<ProjectListState> = projects.observeProjects()
        .map<List<ProjectSummary>, ProjectListState> { summaries -> ProjectListState.Loaded(summaries.map { it.toUi() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectListState.Loading)

    private val _editor = MutableStateFlow<ProjectEditorState?>(null)
    val editor: StateFlow<ProjectEditorState?> = _editor.asStateFlow()

    private val _pendingDelete = MutableStateFlow<ProjectRowUi?>(null)
    val pendingDelete: StateFlow<ProjectRowUi?> = _pendingDelete.asStateFlow()

    /** A newly created project to open; the screen navigates and calls [onProjectOpened]. */
    private val _openProject = MutableStateFlow<String?>(null)
    val openProject: StateFlow<String?> = _openProject.asStateFlow()

    fun startCreate() {
        val start = today()
        _editor.value = ProjectEditorState(null, "", start, start.plusDays(DEFAULT_LENGTH_DAYS - 1))
    }

    fun startEdit(project: ProjectRowUi) {
        _editor.value = ProjectEditorState(project.id, project.name, project.startDate, project.endDate)
    }

    fun onNameChange(name: String) = _editor.update { it?.copy(name = name, problem = null) }

    fun onRangeChange(start: LocalDate, end: LocalDate) =
        _editor.update { it?.copy(startDate = start, endDate = end, problem = null) }

    fun dismissEditor() {
        _editor.value = null
    }

    fun saveEditor() {
        val entered = _editor.value?.takeUnless { it.saving } ?: return
        _editor.value = entered.copy(saving = true)
        viewModelScope.launch {
            when (val result = useCases.save(entered.projectId, entered.name, entered.startDate, entered.endDate)) {
                is SaveProjectResult.Invalid -> {
                    _editor.value = entered.copy(problem = result.problem.toUi(), saving = false)
                    return@launch
                }
                is SaveProjectResult.Created -> _openProject.value = result.projectId
                SaveProjectResult.Updated -> Unit
            }
            _editor.value = null
        }
    }

    fun onProjectOpened() {
        _openProject.value = null
    }

    fun requestDelete(project: ProjectRowUi) {
        _pendingDelete.value = project
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val target = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { useCases.delete(target.id) }
    }

    companion object {
        const val DEFAULT_LENGTH_DAYS = 7L

        fun factory(projects: ProjectQueries, useCases: () -> ProjectUseCases): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProjectListViewModel(projects, useCases()) }
        }
    }
}
