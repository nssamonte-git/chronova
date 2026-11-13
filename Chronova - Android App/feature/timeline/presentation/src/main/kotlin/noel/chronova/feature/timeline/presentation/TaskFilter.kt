package noel.chronova.feature.timeline.presentation

import noel.chronova.core.model.TaskColor

/**
 * What the timeline lists while searching: tasks whose title contains [query] (any
 * case) and that match the chips. Chips of one kind widen the search (blue or green);
 * the kinds narrow it together (blue and done). Kept only while the screen is open.
 */
data class TaskFilter(
    val query: String = "",
    val colors: Set<TaskColor> = emptySet(),
    val kinds: Set<TaskKind> = emptySet(),
) {
    val isActive: Boolean get() = query.isNotBlank() || colors.isNotEmpty() || kinds.isNotEmpty()

    fun matches(task: TaskUi): Boolean {
        val words = query.trim()
        if (words.isNotEmpty() && !task.title.contains(words, ignoreCase = true)) return false
        if (colors.isNotEmpty() && task.color !in colors) return false
        if (kinds.isNotEmpty() && kinds.none { it.matches(task) }) return false
        return true
    }
}

/** The status chips of the search bar. */
enum class TaskKind {
    NOT_DONE,
    DONE,
    MILESTONE,
    ;

    fun matches(task: TaskUi): Boolean = when (this) {
        NOT_DONE -> !task.isDone
        DONE -> task.isDone
        MILESTONE -> task.isMilestone
    }
}

/** The open search bar: the filter, and how many of the project's tasks match it. */
data class TaskSearchUi(val filter: TaskFilter, val matches: Int, val total: Int)
