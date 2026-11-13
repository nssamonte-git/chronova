package noel.chronova.core.domain.schedule

import java.time.ZoneId
import noel.chronova.core.domain.DependencyRules
import noel.chronova.core.domain.TaskRules
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency

/** Pure rules for applying [ScheduleChange]s to a [ScheduleState]. */
object ScheduleChanges {

    /** The state after [changes], and the ids of records whose expected state didn't match (skipped). */
    data class Outcome(val state: ScheduleState, val staleIds: Set<String>)

    /**
     * Applies [changes] in order. A change whose record isn't as the change
     * expects is skipped and its record id reported as stale.
     */
    fun apply(state: ScheduleState, changes: List<ScheduleChange>): Outcome {
        val tasks = LinkedHashMap<String, SchedulerTask>().apply { state.tasks.forEach { put(it.id, it) } }
        val links = LinkedHashMap<String, TaskDependency>().apply { state.links.forEach { put(it.id, it) } }
        val stale = linkedSetOf<String>()
        for (change in changes) {
            when (change) {
                is ScheduleChange.UpdateTask ->
                    if (tasks[change.before.id] == change.before) tasks[change.after.id] = change.after else stale += change.before.id
                is ScheduleChange.CreateTask ->
                    if (change.task.id !in tasks) tasks[change.task.id] = change.task else stale += change.task.id
                is ScheduleChange.DeleteTask ->
                    if (tasks[change.task.id] == change.task) tasks.remove(change.task.id) else stale += change.task.id
                is ScheduleChange.AddLink -> {
                    val link = change.link
                    val duplicate = links.values.any { it.predecessorId == link.predecessorId && it.successorId == link.successorId }
                    if (link.id !in links && !duplicate) links[link.id] = link else stale += link.id
                }
                is ScheduleChange.RemoveLink ->
                    if (links[change.link.id] == change.link) links.remove(change.link.id) else stale += change.link.id
            }
        }
        return Outcome(state.copy(tasks = tasks.values.toList(), links = links.values.toList()), stale)
    }

    /**
     * Why [changes] can't be applied to [state] (null when they can): a record
     * changed since, a changed task breaking [TaskRules], a link to a missing
     * task, or a new link closing a cycle. Pre-existing data is not judged, so an
     * odd row from a sync never blocks unrelated edits.
     */
    fun problemWith(state: ScheduleState?, changes: List<ScheduleChange>, zone: ZoneId): ChangeProblem? {
        if (state == null) return ChangeProblem.ProjectMissing
        val outcome = apply(state, changes)
        if (outcome.staleIds.isNotEmpty()) return ChangeProblem.Stale(outcome.staleIds)

        val result = outcome.state
        val window = state.project.window(zone)
        val tasksById = result.tasks.associateBy { it.id }
        for (change in changes) {
            val written = when (change) {
                is ScheduleChange.UpdateTask -> change.after
                is ScheduleChange.CreateTask -> change.task
                else -> null
            } ?: continue
            val task = tasksById[written.id] ?: continue // deleted later in the same list
            if (task.projectId != state.project.id) return ChangeProblem.Stale(setOf(task.id))
            TaskRules.validate(task, window)
                ?.let { return ChangeProblem.InvalidTask(task.id, it) }
        }
        for (change in changes) {
            when (change) {
                is ScheduleChange.AddLink -> {
                    val link = change.link
                    if (result.links.none { it.id == link.id }) continue // removed later in the same list
                    if (link.projectId != state.project.id || link.predecessorId !in tasksById || link.successorId !in tasksById) {
                        return ChangeProblem.BrokenLink(link.id)
                    }
                    // A cycle through predecessor → successor exists when the successor already leads back to the predecessor.
                    val others = result.links.filter { it.id != link.id }
                    if (link.predecessorId in DependencyRules.forbiddenPredecessors(link.successorId, others)) {
                        return ChangeProblem.Cycle(link.id)
                    }
                }
                is ScheduleChange.DeleteTask -> {
                    val id = change.task.id
                    if (id in tasksById) continue // re-created later in the same list
                    result.links.firstOrNull { it.predecessorId == id || it.successorId == id }
                        ?.let { return ChangeProblem.BrokenLink(it.id) }
                }
                else -> Unit
            }
        }
        return null
    }

    /** The changes that undo [changes] (applied right after them). */
    fun inverse(changes: List<ScheduleChange>): List<ScheduleChange> = changes.asReversed().map { change ->
        when (change) {
            is ScheduleChange.UpdateTask -> ScheduleChange.UpdateTask(before = change.after, after = change.before)
            is ScheduleChange.CreateTask -> ScheduleChange.DeleteTask(change.task)
            is ScheduleChange.DeleteTask -> ScheduleChange.CreateTask(change.task)
            is ScheduleChange.AddLink -> ScheduleChange.RemoveLink(change.link)
            is ScheduleChange.RemoveLink -> ScheduleChange.AddLink(change.link)
        }
    }
}
