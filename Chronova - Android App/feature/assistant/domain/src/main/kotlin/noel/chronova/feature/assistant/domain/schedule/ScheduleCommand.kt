package noel.chronova.feature.assistant.domain.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A scheduling instruction in the user's terms — what a voice or typed command
 * is turned into. Commands name tasks by id and give dates and times in the
 * project's local time; [CommandPlanner] turns them into checked
 * [ScheduleChange]s. Commands never write anything themselves.
 */
sealed interface ScheduleCommand {
    /** Moves the task so it starts at [start], keeping its duration. */
    data class MoveTask(val taskId: String, val start: LocalTarget) : ScheduleCommand

    /** Moves the task earlier (negative) or later by [amount], keeping its duration. */
    data class ShiftTask(val taskId: String, val amount: Amount) : ScheduleCommand

    /** Sets the start and/or end; the other edge stays where it is. */
    data class SetTimes(val taskId: String, val start: LocalDateTime?, val end: LocalDateTime?) : ScheduleCommand {
        init {
            require(start != null || end != null) { "SetTimes needs a start or an end" }
        }
    }

    /** Makes the task longer (positive) or shorter by [amount]; the start stays. */
    data class ChangeDuration(val taskId: String, val amount: Amount) : ScheduleCommand

    /** Makes the task exactly [duration] long; the start stays. */
    data class SetDuration(val taskId: String, val duration: Amount) : ScheduleCommand

    data class RenameTask(val taskId: String, val title: String) : ScheduleCommand

    data class CreateTask(
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val predecessorIds: Set<String> = emptySet(),
    ) : ScheduleCommand

    data class DeleteTask(val taskId: String) : ScheduleCommand

    /** [successorId] should not start before [predecessorId] ends. */
    data class AddDependency(val predecessorId: String, val successorId: String) : ScheduleCommand

    data class RemoveDependency(val predecessorId: String, val successorId: String) : ScheduleCommand

    /**
     * Pushes every task that depends on [taskId] (directly or through a chain)
     * later just enough to start after its predecessors end. Only voice/typed
     * commands use this; dragging never moves other tasks.
     */
    data class RescheduleDependents(val taskId: String) : ScheduleCommand
}

/** A local date, optionally with a time; without one the task keeps its time of day. */
data class LocalTarget(val date: LocalDate, val time: LocalTime? = null)

/** A signed length of time. Days and weeks follow the calendar (a DST day stays one day). */
data class Amount(val value: Long, val unit: AmountUnit)

enum class AmountUnit { MINUTES, HOURS, DAYS, WEEKS }
