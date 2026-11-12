package noel.chronova.app.presentation.projects

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
import noel.chronova.app.domain.ProjectProblem
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.ProjectSummary
import noel.chronova.app.domain.projects.ProjectUseCases
import noel.chronova.app.domain.projects.SaveProjectResult

sealed interface ProjectListState {
    data object Loading : ProjectListState
    data class Loaded(val projects: List<ProjectSummary>) : ProjectListState
}

/** The create/edit sheet. [projectId] is null when creating. [problem] shows after a failed save. */
data class ProjectEditorState(
    val projectId: String?,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val problem: ProjectProblem? = null,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = projectId == null
}

class ProjectListViewModel(
    private val useCases: ProjectUseCases,
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
) : ViewModel() {

    val state: StateFlow<ProjectListState> = useCases.observe()
        .map<List<ProjectSummary>, ProjectListState> { ProjectListState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectListState.Loading)

    private val _editor = MutableStateFlow<ProjectEditorState?>(null)
    val editor: StateFlow<ProjectEditorState?> = _editor.asStateFlow()

    private val _pendingDelete = MutableStateFlow<ProjectSummary?>(null)
    val pendingDelete: StateFlow<ProjectSummary?> = _pendingDelete.asStateFlow()

    /** A newly created project to open; the screen navigates and calls [onProjectOpened]. */
    private val _openProject = MutableStateFlow<String?>(null)
    val openProject: StateFlow<String?> = _openProject.asStateFlow()

    fun startCreate() {
        val start = today()
        _editor.value = ProjectEditorState(null, "", start, start.plusDays(DEFAULT_LENGTH_DAYS - 1))
    }

    fun startEdit(project: Project) {
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
                    _editor.value = entered.copy(problem = result.problem, saving = false)
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

    fun requestDelete(summary: ProjectSummary) {
        _pendingDelete.value = summary
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val target = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { useCases.delete(target.project.id) }
    }

    companion object {
        const val DEFAULT_LENGTH_DAYS = 7L

        fun factory(useCases: () -> ProjectUseCases): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProjectListViewModel(useCases()) }
        }
    }
}
