package noel.chronova.feature.assistant.domain

import kotlinx.coroutines.flow.first
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import noel.chronova.core.domain.repository.ProjectRepository
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.domain.schedule.ScheduleState
import noel.chronova.feature.assistant.domain.schedule.CommandProblem

// From words to a plan: the request is interpreted by the server and planned against the schedule as it is now.

/** Changes the assistant will ask about: their lines, the changes, the clashes they would cause, and a clash-free alternative. */
data class VoicePlan(
    val items: List<PreviewItem>,
    val changes: List<ScheduleChange>,
    val warnings: List<ScheduleWarning> = emptyList(),
    /** A clash-free version to offer instead, when [warnings] has clashes and one was found. */
    val alternative: VoicePlan? = null,
)

/** What a request turned into. */
sealed interface Interpretation {
    /** Changes to ask about; [actions] are done at once, before asking. */
    data class Planned(val plan: VoicePlan, val actions: List<VoiceAction> = emptyList()) : Interpretation

    /** Only things to do at once (nothing changes). */
    data class Act(val actions: List<VoiceAction>) : Interpretation

    /** Undo the assistant's last applied change (only it knows what that was); asked about first. */
    data class UndoLast(val actions: List<VoiceAction> = emptyList()) : Interpretation
    data class Question(val text: String) : Interpretation
    data class Unsupported(val reason: String?) : Interpretation
    data class CannotDo(val problem: CommandProblem, val titles: Map<String, String>, val projectEndMillis: Long) : Interpretation
    data object NothingToChange : Interpretation

    /** The schedule changed (or the project went) meanwhile: nothing more can be done in this conversation. */
    data object ScheduleChanged : Interpretation
    data object Unreadable : Interpretation
}

/**
 * The server interprets the words; the app plans them against the schedule as it is now
 * ([AICommandHandler]). A plan that would cause clashes gets a clash-free alternative when
 * one exists: the model's, if it is valid and clash-free here (the one that came with the
 * proposal, else one asked for now), else the app's own (push what waits for a task just enough).
 */
class InterpretRequest(
    private val repository: ProjectRepository,
    private val voiceApi: VoiceApi,
    private val token: VoiceAccessToken,
    private val handler: AICommandHandler,
    private val zone: ZoneId,
    private val clock: () -> Long,
) {
    /**
     * [projectId]: the open project, or null on the list of projects. [view]: what the timeline shows,
     * sent so "this task" or "zoom in" can be understood.
     */
    suspend operator fun invoke(projectId: String?, text: String, view: ViewContext? = null): Interpretation {
        val projects = repository.observeProjects().first().map { ProjectRef(it.project.id, it.project.name, it.project.startDate, it.project.endDate) }
        if (projectId == null) return onList(text, projects)
        val sent = repository.schedule(projectId) ?: return Interpretation.ScheduleChanged
        val context = VoiceContext.from(sent, zone, clock(), view, projects)
        val proposal = voiceApi.interpret(token(), text, context)
        val now = repository.schedule(projectId) ?: return Interpretation.ScheduleChanged
        return when (val handled = handler.handle(context, now, proposal)) {
            is HandledProposal.Preview -> {
                val planned = VoicePlan(PreviewItem.from(handled.changes, now), handled.changes, ScheduleWarning.from(handled.changes, now))
                Interpretation.Planned(
                    if (planned.warnings.isEmpty()) planned else planned.copy(alternative = alternative(text, context, now, proposal, planned)),
                    handled.actions,
                )
            }
            is HandledProposal.Act -> Interpretation.Act(handled.actions)
            is HandledProposal.UndoLast -> Interpretation.UndoLast(handled.actions)
            is HandledProposal.Clarify -> Interpretation.Question(handled.question)
            is HandledProposal.Unsupported -> Interpretation.Unsupported(handled.reason)
            is HandledProposal.CannotDo -> Interpretation.CannotDo(
                handled.problem,
                now.tasks.associate { it.id to it.title },
                now.project.window(zone).endMillis,
            )
            HandledProposal.NothingToChange -> Interpretation.NothingToChange
            HandledProposal.ScheduleChanged -> Interpretation.ScheduleChanged
            HandledProposal.Unreadable -> Interpretation.Unreadable
        }
    }

    /** From the list of projects only actions make sense (the server says to open a project for anything else). */
    private suspend fun onList(text: String, projects: List<ProjectRef>): Interpretation {
        val proposal = voiceApi.interpretOnList(token(), text, ListContext.from(projects, zone, clock()))
        return when (proposal.result) {
            "actions" -> proposal.actions
                .map { VoiceAction.from(it, tasks = emptyList(), projects) ?: return Interpretation.Unreadable }
                .takeIf { it.isNotEmpty() }
                ?.let(Interpretation::Act)
                ?: Interpretation.Unreadable
            "clarification" -> proposal.question?.takeIf { it.isNotBlank() }?.let(Interpretation::Question) ?: Interpretation.Unreadable
            "unsupported" -> Interpretation.Unsupported(proposal.reason?.takeIf { it.isNotBlank() })
            else -> Interpretation.Unreadable
        }
    }

    private suspend fun alternative(
        text: String,
        context: VoiceContext,
        state: ScheduleState,
        proposal: Proposal,
        planned: VoicePlan,
    ): VoicePlan? {
        fun usable(candidate: Proposal): VoicePlan? {
            val handled = handler.handle(context, state, candidate) as? HandledProposal.Preview ?: return null
            if (ScheduleWarning.from(handled.changes, state).isNotEmpty()) return null
            val items = PreviewItem.from(handled.changes, state)
            // The same change in other words is no alternative.
            if (ExplainedChange.from(items, zone) == ExplainedChange.from(planned.items, zone)) return null
            return VoicePlan(items, handled.changes)
        }
        // Usually the interpreter saw the clash and answered with an alternative already: no extra round trip.
        proposal.alternative?.takeIf { it.isNotEmpty() }
            ?.let { usable(proposal.copy(operations = it, alternative = null, actions = emptyList())) }
            ?.let { return it }
        val suggested = try {
            voiceApi.resolve(token(), text, context, proposal.operations, planned.warnings)
        } catch (error: CancellationException) {
            throw error
        } catch (_: VoiceApiException) {
            null
        }
        suggested?.takeIf { it.result == "operations" }?.let(::usable)?.let { return it }
        val waitedFor = ScheduleWarning.waitedFor(planned.changes, state)
        if (waitedFor.isEmpty()) return null
        return usable(proposal.copy(operations = proposal.operations + waitedFor.map { ProposedOperation("reschedule_dependents", taskId = it) }, actions = emptyList()))
    }
}
