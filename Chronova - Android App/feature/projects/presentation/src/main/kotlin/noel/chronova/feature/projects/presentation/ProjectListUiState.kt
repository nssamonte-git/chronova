package noel.chronova.feature.projects.presentation

import java.time.LocalDate

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
