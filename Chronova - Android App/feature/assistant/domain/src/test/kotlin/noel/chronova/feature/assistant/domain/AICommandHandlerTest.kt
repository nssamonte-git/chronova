package noel.chronova.feature.assistant.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.model.TaskDependency
import noel.chronova.feature.assistant.domain.schedule.CommandPlanner
import noel.chronova.feature.assistant.domain.schedule.CommandProblem
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.domain.schedule.ScheduleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AICommandHandlerTest {

    private val zone = ZoneOffset.UTC
    private val link = TaskDependency("l1", SampleTasks.PROJECT_ID, "ui-design", "implementation")
    private val state = ScheduleState(SampleTasks.project, SampleTasks.tasks(zone), listOf(link))
    private val now = SampleTasks.REFERENCE_DATE.atTime(8, 5).toInstant(zone).toEpochMilli()
    private val context = VoiceContext.from(state, zone, now)
    private val handler = AICommandHandler(CommandPlanner(zone, newId = { "new" }))

    private fun proposal(vararg operations: ProposedOperation, revision: String? = context.revision) =
        Proposal(revision, "operations", operations.toList(), null, null)

    private fun actions(vararg actions: ProposedAction, result: String = "actions", operations: List<ProposedOperation> = emptyList()) =
        Proposal(context.revision, result, operations, null, null, actions = actions.toList())

    @Test
    fun actionsAloneAreDoneWithoutAPreview() {
        val handled = handler.handle(
            context,
            state,
            actions(ProposedAction("go_to_today"), ProposedAction("go_to_date", date = "2026-03-03"), ProposedAction("show_task", taskId = "review")),
        )
        assertEquals(
            HandledProposal.Act(listOf(VoiceAction.GoToToday, VoiceAction.GoToDate(LocalDate.of(2026, 3, 3)), VoiceAction.ShowTask("review", "Review"))),
            handled,
        )
    }

    @Test
    fun actionsThatComeWithChangesAreDoneBeforeTheChangesAreAskedAbout() {
        val delete = ProposedOperation("delete_task", taskId = "review")
        val handled = handler.handle(context, state, actions(ProposedAction("show_task", taskId = "qa-testing"), result = "operations", operations = listOf(delete)))
        assertTrue(handled is HandledProposal.Preview)
        assertEquals(listOf(VoiceAction.ShowTask("qa-testing", "QA Testing")), (handled as HandledProposal.Preview).actions)
    }

    @Test
    fun anActionThisAppCantDoOrForATaskThatIsntThereIsUnreadable() {
        for (bad in listOf(
            actions(ProposedAction("format_phone")),
            actions(ProposedAction("show_task", taskId = "gone")),
            actions(ProposedAction("go_to_date", date = "2026-02-30")),
            actions(ProposedAction("go_to_date")),
            actions(),
            actions(ProposedAction("go_to_today"), operations = listOf(ProposedOperation("delete_task", taskId = "review"))),
        )) {
            assertEquals(bad.toString(), HandledProposal.Unreadable, handler.handle(context, state, bad))
        }
    }

    @Test
    fun contextIsLocalTimeAndListsPredecessors() {
        assertEquals(LocalDate.of(2026, 3, 2), context.today)
        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 5), context.now)
        assertEquals("UTC", context.timeZone)
        val implementation = context.tasks.first { it.id == "implementation" }
        assertEquals(LocalDateTime.of(2026, 3, 2, 11, 0), implementation.start)
        assertEquals(listOf("ui-design"), implementation.predecessorIds)
    }

    @Test
    fun contextCarriesProgressAndMilestonesButNeverNotes() {
        val tracked = state.copy(
            tasks = state.tasks.map { if (it.id == "review") it.copy(endMillis = it.startMillis, progress = 100, isMilestone = true, notes = "private") else it },
        )
        val review = VoiceContext.from(tracked, ZoneOffset.UTC, 0L).tasks.first { it.id == "review" }
        assertEquals(100, review.progress)
        assertTrue(review.milestone)
        assertTrue("private" !in review.toString())
    }

    @Test
    fun revisionFollowsContent() {
        assertEquals(context.revision, ScheduleRevision.of(state.copy()))
        val renamed = state.copy(tasks = state.tasks.map { if (it.id == "review") it.copy(title = "Sign-off") else it })
        assertNotEquals(context.revision, ScheduleRevision.of(renamed))
    }

    @Test
    fun operationsArePlannedIntoAPreview() {
        val handled = handler.handle(
            context,
            state,
            proposal(
                ProposedOperation("change_duration", taskId = "ui-design", amount = 1, unit = "hours"),
                ProposedOperation("reschedule_dependents", taskId = "ui-design"),
            ),
        )
        val changes = (handled as HandledProposal.Preview).changes
        assertEquals(listOf("ui-design", "implementation"), changes.map { (it as ScheduleChange.UpdateTask).after.id })
    }

    @Test
    fun everyOperationTypeMaps() {
        val ops = listOf(
            ProposedOperation("move_task", taskId = "review", date = "2026-03-03", time = "10:30"),
            ProposedOperation("shift_task", taskId = "review", amount = -2, unit = "minutes"),
            ProposedOperation("set_times", taskId = "review", start = "2026-03-02T15:00", end = null),
            ProposedOperation("change_duration", taskId = "review", amount = 1, unit = "days"),
            ProposedOperation("set_duration", taskId = "review", amount = 2, unit = "weeks"),
            ProposedOperation("rename_task", taskId = "review", title = "Sign-off"),
            ProposedOperation("create_task", title = "QA 2", start = "2026-03-03T09:00", end = "2026-03-03T10:00", predecessorIds = listOf("review")),
            ProposedOperation("delete_task", taskId = "review"),
            ProposedOperation("add_dependency", predecessorId = "review", successorId = "qa-testing"),
            ProposedOperation("remove_dependency", predecessorId = "review", successorId = "qa-testing"),
            ProposedOperation("reschedule_dependents", taskId = "review"),
            ProposedOperation("set_progress", taskId = "review", percent = 100),
            ProposedOperation("create_milestone", title = "Launch", at = "2026-03-04T17:00", predecessorIds = listOf("review")),
        )
        ops.forEach { assertTrue(it.type, handler.toCommand(it) != null) }
    }

    @Test
    fun malformedOperationsAreUnreadable() {
        listOf(
            ProposedOperation("move_task", taskId = "review", date = "2026-02-30"),
            ProposedOperation("move_task", taskId = "review"),
            ProposedOperation("shift_task", taskId = "review", amount = 2, unit = "fortnights"),
            ProposedOperation("shift_task", taskId = "review", amount = 0, unit = "days"),
            ProposedOperation("set_times", taskId = "review"),
            ProposedOperation("set_progress", taskId = "review"),
            ProposedOperation("create_milestone", title = "Launch", at = "2026-03-04"),
            ProposedOperation("drop_table"),
        ).forEach { op ->
            assertEquals(op.toString(), HandledProposal.Unreadable, handler.handle(context, state, proposal(op)))
        }
        assertEquals(HandledProposal.Unreadable, handler.handle(context, state, proposal()))
        assertEquals(HandledProposal.Unreadable, handler.handle(context, state, Proposal(context.revision, "dance", emptyList(), null, null)))
    }

    @Test
    fun aChangedScheduleIsNeverPlannedOnto() {
        val moved = state.copy(tasks = state.tasks.map { if (it.id == "review") it.copy(title = "Edited meanwhile") else it })
        val op = ProposedOperation("delete_task", taskId = "ui-design")
        assertEquals(HandledProposal.ScheduleChanged, handler.handle(context, moved, proposal(op)))
        assertEquals(HandledProposal.ScheduleChanged, handler.handle(context, null, proposal(op)))
        assertEquals(HandledProposal.ScheduleChanged, handler.handle(context, state, proposal(op, revision = "other")))
    }

    @Test
    fun questionsRefusalsAndProblemsPassThrough() {
        assertEquals(
            HandledProposal.Clarify("Which review?"),
            handler.handle(context, state, Proposal(context.revision, "clarification", emptyList(), "Which review?", null)),
        )
        assertEquals(
            HandledProposal.Unsupported(null),
            handler.handle(context, state, Proposal(context.revision, "unsupported", emptyList(), null, " ")),
        )
        assertEquals(
            HandledProposal.CannotDo(CommandProblem.WouldCycle("implementation", "ui-design")),
            handler.handle(context, state, proposal(ProposedOperation("add_dependency", predecessorId = "implementation", successorId = "ui-design"))),
        )
        assertEquals(
            HandledProposal.NothingToChange,
            handler.handle(context, state, proposal(ProposedOperation("reschedule_dependents", taskId = "review"))),
        )
    }

    @Test
    fun previewListsTasksOnceAndHidesLinksOfDeletedTasks() {
        val handled = handler.handle(context, state, proposal(ProposedOperation("delete_task", taskId = "ui-design"))) as HandledProposal.Preview
        val items = PreviewItem.from(handled.changes, state)
        assertEquals(listOf(PreviewItem.Removed(state.tasks[0])), items)

        val unlinked = handler.handle(
            context,
            state,
            proposal(ProposedOperation("remove_dependency", predecessorId = "ui-design", successorId = "implementation")),
        ) as HandledProposal.Preview
        assertEquals(listOf(PreviewItem.Unlinked("UI Design", "Implementation")), PreviewItem.from(unlinked.changes, state))
    }
}
