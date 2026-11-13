package noel.chronova.feature.assistant.domain.schedule

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import noel.chronova.core.domain.DependencyRules
import noel.chronova.core.domain.TaskProblem
import noel.chronova.core.domain.TaskRules
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.domain.schedule.ChangeProblem
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.domain.schedule.ScheduleChanges
import noel.chronova.core.domain.schedule.ScheduleState

/** Why a command can't be carried out. */
sealed interface CommandProblem {
    /** No live task has this id. */
    data class UnknownTask(val taskId: String) : CommandProblem

    /** The task (named by its title) would break a task rule. */
    data class InvalidTask(val title: String, val problem: TaskProblem) : CommandProblem

    /** Linking would make a task depend on itself, directly or through a chain. */
    data class WouldCycle(val predecessorId: String, val successorId: String) : CommandProblem

    data class AlreadyLinked(val predecessorId: String, val successorId: String) : CommandProblem

    data class NotLinked(val predecessorId: String, val successorId: String) : CommandProblem

    /** Rescheduling would push these tasks past the project's end. */
    data class DoesNotFit(val taskIds: List<String>) : CommandProblem

    /** The planned changes failed the final check (shouldn't happen; a safety net). */
    data class Rejected(val problem: ChangeProblem) : CommandProblem
}

/**
 * Turns [ScheduleCommand]s into one list of [ScheduleChange]s, applying them in
 * order to a working copy so later commands see earlier ones ("move design, then
 * push what depends on it"). Dates and times are read in [zone], the zone the
 * project's days are shown in. Nothing is written; the caller previews the
 * result and applies it with [ApplyScheduleChanges].
 */
class CommandPlanner(
    private val zone: ZoneId,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    sealed interface Plan {
        /** The changes to preview and apply; empty when the commands change nothing. */
        data class Ready(val changes: List<ScheduleChange>) : Plan

        /** Command number [commandIndex] (0-based) can't be carried out; nothing is planned. */
        data class Failed(val commandIndex: Int, val problem: CommandProblem) : Plan
    }

    fun plan(state: ScheduleState, commands: List<ScheduleCommand>): Plan {
        val window = state.project.window(zone)
        var working = state
        val all = mutableListOf<ScheduleChange>()
        commands.forEachIndexed { index, command ->
            val changes = when (val result = changesFor(working, command)) {
                is Step.Changes -> result.changes
                is Step.Problem -> return Plan.Failed(index, result.problem)
            }
            for (change in changes) {
                val task = when (change) {
                    is ScheduleChange.UpdateTask -> change.after
                    is ScheduleChange.CreateTask -> change.task
                    else -> null
                } ?: continue
                TaskRules.validate(task, window)
                    ?.let { return Plan.Failed(index, CommandProblem.InvalidTask(task.title, it)) }
            }
            working = ScheduleChanges.apply(working, changes).state
            all += changes
        }
        val compacted = compact(all)
        ScheduleChanges.problemWith(state, compacted, zone)?.let { return Plan.Failed(commands.lastIndex, CommandProblem.Rejected(it)) }
        return Plan.Ready(compacted)
    }

    private sealed interface Step {
        data class Changes(val changes: List<ScheduleChange>) : Step
        data class Problem(val problem: CommandProblem) : Step
    }

    private fun changesFor(state: ScheduleState, command: ScheduleCommand): Step {
        fun task(id: String) = state.tasks.firstOrNull { it.id == id }
        fun update(id: String, edit: (SchedulerTask) -> SchedulerTask): Step {
            val task = task(id) ?: return Step.Problem(CommandProblem.UnknownTask(id))
            return Step.Changes(listOf(ScheduleChange.UpdateTask(task, edit(task))))
        }

        return when (command) {
            is ScheduleCommand.MoveTask -> update(command.taskId) { task ->
                val time = command.start.time ?: local(task.startMillis).toLocalTime()
                val start = millis(command.start.date.atTime(time))
                task.copy(startMillis = start, endMillis = start + task.durationMillis)
            }
            is ScheduleCommand.ShiftTask -> update(command.taskId) { task ->
                task.copy(startMillis = shift(task.startMillis, command.amount), endMillis = shift(task.endMillis, command.amount))
            }
            is ScheduleCommand.SetTimes -> {
                val task = task(command.taskId) ?: return Step.Problem(CommandProblem.UnknownTask(command.taskId))
                val start = command.start?.let(::millis) ?: task.startMillis
                val end = command.end?.let(::millis) ?: task.endMillis
                if (end <= start) return Step.Problem(CommandProblem.InvalidTask(task.title, TaskProblem.EndNotAfterStart))
                Step.Changes(listOf(ScheduleChange.UpdateTask(task, task.copy(startMillis = start, endMillis = end))))
            }
            is ScheduleCommand.ChangeDuration -> update(command.taskId) { task ->
                task.copy(endMillis = shift(task.endMillis, command.amount).coerceAtLeast(task.startMillis + 1))
            }
            is ScheduleCommand.SetDuration -> update(command.taskId) { task ->
                task.copy(endMillis = shift(task.startMillis, command.duration).coerceAtLeast(task.startMillis + 1))
            }
            is ScheduleCommand.RenameTask -> update(command.taskId) { it.copy(title = command.title.trim()) }
            is ScheduleCommand.CreateTask -> create(state, command)
            is ScheduleCommand.DeleteTask -> {
                val task = task(command.taskId) ?: return Step.Problem(CommandProblem.UnknownTask(command.taskId))
                val links = state.links.filter { it.predecessorId == task.id || it.successorId == task.id }
                Step.Changes(links.map { ScheduleChange.RemoveLink(it) } + ScheduleChange.DeleteTask(task))
            }
            is ScheduleCommand.AddDependency -> {
                val (p, s) = command.predecessorId to command.successorId
                listOf(p, s).firstOrNull { task(it) == null }?.let { return Step.Problem(CommandProblem.UnknownTask(it)) }
                when {
                    state.links.any { it.predecessorId == p && it.successorId == s } -> Step.Problem(CommandProblem.AlreadyLinked(p, s))
                    p == s || !DependencyRules.canLink(p, s, state.links) -> Step.Problem(CommandProblem.WouldCycle(p, s))
                    else -> Step.Changes(listOf(ScheduleChange.AddLink(TaskDependency(newId(), state.project.id, p, s))))
                }
            }
            is ScheduleCommand.RemoveDependency -> {
                val link = state.links.firstOrNull { it.predecessorId == command.predecessorId && it.successorId == command.successorId }
                    ?: return Step.Problem(CommandProblem.NotLinked(command.predecessorId, command.successorId))
                Step.Changes(listOf(ScheduleChange.RemoveLink(link)))
            }
            is ScheduleCommand.RescheduleDependents -> {
                if (task(command.taskId) == null) return Step.Problem(CommandProblem.UnknownTask(command.taskId))
                when (val result = DependentsRescheduler.reschedule(state, command.taskId, state.project.window(zone))) {
                    is DependentsRescheduler.Result.Moves -> Step.Changes(result.updates)
                    is DependentsRescheduler.Result.DoesNotFit -> Step.Problem(CommandProblem.DoesNotFit(result.taskIds))
                }
            }
        }
    }

    private fun create(state: ScheduleState, command: ScheduleCommand.CreateTask): Step {
        command.predecessorIds.firstOrNull { id -> state.tasks.none { it.id == id } }
            ?.let { return Step.Problem(CommandProblem.UnknownTask(it)) }
        val start = millis(command.start)
        val end = millis(command.end)
        if (end <= start) return Step.Problem(CommandProblem.InvalidTask(command.title.trim(), TaskProblem.EndNotAfterStart))
        val task = SchedulerTask(
            id = newId(),
            projectId = state.project.id,
            title = command.title.trim(),
            startMillis = start,
            endMillis = end,
            color = TaskColor.entries[state.tasks.size % TaskColor.entries.size],
            sortOrder = (state.tasks.maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        val links = command.predecessorIds.map { ScheduleChange.AddLink(TaskDependency(newId(), state.project.id, it, task.id)) }
        return Step.Changes(listOf(ScheduleChange.CreateTask(task)) + links)
    }

    private fun millis(local: LocalDateTime): Long = local.atZone(zone).toInstant().toEpochMilli()

    private fun local(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone)

    private fun shift(millis: Long, amount: Amount): Long = when (amount.unit) {
        AmountUnit.MINUTES -> millis + amount.value * 60_000L
        AmountUnit.HOURS -> millis + amount.value * 3_600_000L
        AmountUnit.DAYS -> local(millis).plusDays(amount.value).toInstant().toEpochMilli()
        AmountUnit.WEEKS -> local(millis).plusWeeks(amount.value).toInstant().toEpochMilli()
    }

    /**
     * Folds repeated edits of one task into a single change (first "before",
     * last "after"), so the preview lists each task once and undo is one step.
     * Updates that end where they started are dropped.
     */
    private fun compact(changes: List<ScheduleChange>): List<ScheduleChange> {
        val out = mutableListOf<ScheduleChange?>()
        val writeIndex = HashMap<String, Int>()
        for (change in changes) {
            if (change is ScheduleChange.UpdateTask) {
                val at = writeIndex[change.after.id]
                when (val earlier = at?.let { out[it] }) {
                    is ScheduleChange.UpdateTask -> { out[at] = ScheduleChange.UpdateTask(earlier.before, change.after); continue }
                    is ScheduleChange.CreateTask -> { out[at] = ScheduleChange.CreateTask(change.after); continue }
                    else -> writeIndex[change.after.id] = out.size
                }
            } else if (change is ScheduleChange.CreateTask) {
                writeIndex[change.task.id] = out.size
            } else if (change is ScheduleChange.DeleteTask) {
                val at = writeIndex.remove(change.task.id)
                val earlier = at?.let { out[it] }
                if (earlier is ScheduleChange.UpdateTask) {
                    out[at] = null
                    out += ScheduleChange.DeleteTask(earlier.before)
                    continue
                }
            }
            out += change
        }
        return out.filterNotNull().filterNot { it is ScheduleChange.UpdateTask && it.before == it.after }
    }
}
