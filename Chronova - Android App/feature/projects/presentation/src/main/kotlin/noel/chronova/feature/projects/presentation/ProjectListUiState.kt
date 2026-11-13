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
    /** When set, the new project is a copy of this one (its length is kept, so only the start is picked). */
    val copyFrom: ProjectSourceUi? = null,
    /** Projects a new one can start from ("Start from"); empty when editing. */
    val sources: List<ProjectSourceUi> = emptyList(),
) {
    val isNew: Boolean get() = projectId == null
    val isCopy: Boolean get() = copyFrom != null
}
