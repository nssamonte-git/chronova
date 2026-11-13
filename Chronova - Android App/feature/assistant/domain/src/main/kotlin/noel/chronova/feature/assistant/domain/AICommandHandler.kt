package noel.chronova.feature.assistant.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException
import noel.chronova.feature.assistant.domain.schedule.Amount
import noel.chronova.feature.assistant.domain.schedule.AmountUnit
import noel.chronova.feature.assistant.domain.schedule.CommandPlanner
import noel.chronova.feature.assistant.domain.schedule.CommandProblem
import noel.chronova.feature.assistant.domain.schedule.LocalTarget
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.feature.assistant.domain.schedule.ScheduleCommand
import noel.chronova.core.domain.schedule.ScheduleState

/** The server's answer to an interpret request, as received (`docs/voice-api.md`). */
data class Proposal(
    val revision: String?,
    val result: String,
    val operations: List<ProposedOperation>,
    val question: String?,
    val reason: String?,
    /** The model's clash-free version of [operations], when it saw they would clash; checked by the app like any proposal. */
    val alternative: List<ProposedOperation>? = null,
    /** Things to do at once, without a yes: alone (result "actions") or before the operations are asked about. */
    val actions: List<ProposedAction> = emptyList(),
)

/** One proposed operation; which fields are set depends on [type]. */
data class ProposedOperation(
    val type: String,
    val taskId: String? = null,
    val date: String? = null,
    val time: String? = null,
    val amount: Long? = null,
    val unit: String? = null,
    val start: String? = null,
    val end: String? = null,
    val title: String? = null,
    val predecessorIds: List<String>? = null,
    val predecessorId: String? = null,
    val successorId: String? = null,
    /** `set_progress`: percent done, 0–100. */
    val percent: Int? = null,
    /** `create_milestone`: when it happens (local date-time). */
    val at: String? = null,
)

/** What to show the user for a proposal. Nothing has been written. */
sealed interface HandledProposal {
    /** Changes to preview; applied only when the user confirms. [actions] are done first, at once. */
    data class Preview(val changes: List<ScheduleChange>, val actions: List<VoiceAction> = emptyList()) : HandledProposal

    /** Only things to do at once: nothing changes, so nothing is asked. */
    data class Act(val actions: List<VoiceAction>) : HandledProposal

    /** Undo the last applied change: the caller knows what it was, and asks first. */
    data class UndoLast(val actions: List<VoiceAction>) : HandledProposal

    data class Clarify(val question: String) : HandledProposal

    data class Unsupported(val reason: String?) : HandledProposal

    /** The proposal can't be carried out on the current schedule. */
    data class CannotDo(val problem: CommandProblem) : HandledProposal

    /** The proposal was understood but changes nothing. */
    data object NothingToChange : HandledProposal

    /** The schedule changed while the request was being answered; ask again. */
    data object ScheduleChanged : HandledProposal

    /** The answer isn't one this app understands (e.g. a newer server). */
    data object Unreadable : HandledProposal
}

/**
 * Turns a server proposal into commands and plans them against the schedule as
 * it is now. AI output never touches the database or the screen directly: it
 * goes through [CommandPlanner] (and later [noel.chronova.core.domain.schedule.ApplyScheduleChanges]),
 * which apply the same rules as manual edits.
 */
class AICommandHandler(private val planner: CommandPlanner) {

    /**
     * @param sent the schedule as it was sent with the request
     * @param current the schedule now (null if the project is gone)
     */
    fun handle(sent: VoiceContext, current: ScheduleState?, proposal: Proposal): HandledProposal {
        if (current == null || proposal.revision != sent.revision || ScheduleRevision.of(current) != sent.revision) {
            return HandledProposal.ScheduleChanged
        }
        val actions = proposal.actions.map { VoiceAction.from(it, current) ?: return HandledProposal.Unreadable }
        return when (proposal.result) {
            "clarification" -> proposal.question?.takeIf { it.isNotBlank() }?.let { HandledProposal.Clarify(it) } ?: HandledProposal.Unreadable
            "unsupported" -> HandledProposal.Unsupported(proposal.reason?.takeIf { it.isNotBlank() })
            "actions" -> if (actions.isEmpty() || proposal.operations.isNotEmpty()) HandledProposal.Unreadable else HandledProposal.Act(actions)
            "operations" -> {
                if (proposal.operations.any { it.type == UNDO }) {
                    return if (proposal.operations.size == 1) HandledProposal.UndoLast(actions) else HandledProposal.Unreadable
                }
                val commands = proposal.operations.map { toCommand(it) ?: return HandledProposal.Unreadable }
                if (commands.isEmpty()) return HandledProposal.Unreadable
                when (val plan = planner.plan(current, commands)) {
                    is CommandPlanner.Plan.Ready -> when {
                        plan.changes.isNotEmpty() -> HandledProposal.Preview(plan.changes, actions)
                        // Nothing to change, but what was asked to be shown still is.
                        actions.isNotEmpty() -> HandledProposal.Act(actions)
                        else -> HandledProposal.NothingToChange
                    }
                    is CommandPlanner.Plan.Failed -> HandledProposal.CannotDo(plan.problem)
                }
            }
            else -> HandledProposal.Unreadable
        }
    }

    /** The command for one operation, or null if it's malformed. */
    fun toCommand(op: ProposedOperation): ScheduleCommand? = try {
        when (op.type) {
            "move_task" -> ScheduleCommand.MoveTask(op.taskId.req(), LocalTarget(LocalDate.parse(op.date.req()), op.time?.let(LocalTime::parse)))
            "shift_task" -> ScheduleCommand.ShiftTask(op.taskId.req(), amount(op) ?: return null)
            "set_times" -> {
                val start = op.start?.let(LocalDateTime::parse)
                val end = op.end?.let(LocalDateTime::parse)
                if (start == null && end == null) null else ScheduleCommand.SetTimes(op.taskId.req(), start, end)
            }
            "change_duration" -> ScheduleCommand.ChangeDuration(op.taskId.req(), amount(op) ?: return null)
            "set_duration" -> ScheduleCommand.SetDuration(op.taskId.req(), amount(op) ?: return null)
            "rename_task" -> ScheduleCommand.RenameTask(op.taskId.req(), op.title.req())
            "create_task" -> ScheduleCommand.CreateTask(
                title = op.title.req(),
                start = LocalDateTime.parse(op.start.req()),
                end = LocalDateTime.parse(op.end.req()),
                predecessorIds = op.predecessorIds.orEmpty().toSet(),
            )
            "delete_task" -> ScheduleCommand.DeleteTask(op.taskId.req())
            "add_dependency" -> ScheduleCommand.AddDependency(op.predecessorId.req(), op.successorId.req())
            "remove_dependency" -> ScheduleCommand.RemoveDependency(op.predecessorId.req(), op.successorId.req())
            "reschedule_dependents" -> ScheduleCommand.RescheduleDependents(op.taskId.req())
            "set_progress" -> ScheduleCommand.SetProgress(op.taskId.req(), op.percent.req())
            "create_milestone" -> ScheduleCommand.CreateMilestone(
                title = op.title.req(),
                at = LocalDateTime.parse(op.at.req()),
                predecessorIds = op.predecessorIds.orEmpty().toSet(),
            )
            else -> null
        }
    } catch (_: MissingField) {
        null
    } catch (_: DateTimeParseException) {
        null
    }

    private class MissingField : RuntimeException()

    private companion object {
        /** The operation that undoes the assistant's last change (always alone). */
        const val UNDO = "undo_last_change"
    }

    private fun <T : Any> T?.req(): T = this ?: throw MissingField()

    private fun amount(op: ProposedOperation): Amount? {
        val value = op.amount?.takeIf { it != 0L } ?: return null
        val unit = when (op.unit) {
            "minutes" -> AmountUnit.MINUTES
            "hours" -> AmountUnit.HOURS
            "days" -> AmountUnit.DAYS
            "weeks" -> AmountUnit.WEEKS
            else -> return null
        }
        return Amount(value, unit)
    }
}
