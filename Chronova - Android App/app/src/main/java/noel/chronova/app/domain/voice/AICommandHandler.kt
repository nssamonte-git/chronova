package noel.chronova.app.domain.voice

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException
import noel.chronova.app.domain.schedule.Amount
import noel.chronova.app.domain.schedule.AmountUnit
import noel.chronova.app.domain.schedule.CommandPlanner
import noel.chronova.app.domain.schedule.CommandProblem
import noel.chronova.app.domain.schedule.LocalTarget
import noel.chronova.app.domain.schedule.ScheduleChange
import noel.chronova.app.domain.schedule.ScheduleCommand
import noel.chronova.app.domain.schedule.ScheduleState

/** The server's answer to an interpret request, as received (`docs/voice-api.md`). */
data class Proposal(
    val revision: String?,
    val result: String,
    val operations: List<ProposedOperation>,
    val question: String?,
    val reason: String?,
    /** The model's clash-free version of [operations], when it saw they would clash; checked by the app like any proposal. */
    val alternative: List<ProposedOperation>? = null,
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
)

/** What to show the user for a proposal. Nothing has been written. */
sealed interface HandledProposal {
    /** Changes to preview; applied only when the user confirms. */
    data class Preview(val changes: List<ScheduleChange>) : HandledProposal

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
 * goes through [CommandPlanner] (and later [noel.chronova.app.domain.schedule.ApplyScheduleChanges]),
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
        return when (proposal.result) {
            "clarification" -> proposal.question?.takeIf { it.isNotBlank() }?.let { HandledProposal.Clarify(it) } ?: HandledProposal.Unreadable
            "unsupported" -> HandledProposal.Unsupported(proposal.reason?.takeIf { it.isNotBlank() })
            "operations" -> {
                val commands = proposal.operations.map { toCommand(it) ?: return HandledProposal.Unreadable }
                if (commands.isEmpty()) return HandledProposal.Unreadable
                when (val plan = planner.plan(current, commands)) {
                    is CommandPlanner.Plan.Ready ->
                        if (plan.changes.isEmpty()) HandledProposal.NothingToChange else HandledProposal.Preview(plan.changes)
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
            else -> null
        }
    } catch (_: MissingField) {
        null
    } catch (_: DateTimeParseException) {
        null
    }

    private class MissingField : RuntimeException()

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
