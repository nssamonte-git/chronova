package noel.chronova.app.domain.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.TaskProblem
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskDependency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPlannerTest {

    private val zone = ZoneOffset.UTC
    private val day = SampleTasks.REFERENCE_DATE // Mon 2 Mar 2026; the project runs 2–4 Mar
    private var ids = 0
    private val planner = CommandPlanner(zone, newId = { "id${++ids}" })
    private val state = ScheduleState(SampleTasks.project, SampleTasks.tasks(zone), emptyList())

    private fun at(date: LocalDate, hour: Int, minute: Int = 0, z: ZoneId = zone) =
        date.atTime(hour, minute).atZone(z).toInstant().toEpochMilli()

    private fun ready(plan: CommandPlanner.Plan): List<ScheduleChange> {
        assertTrue("expected a plan, got $plan", plan is CommandPlanner.Plan.Ready)
        return (plan as CommandPlanner.Plan.Ready).changes
    }

    private fun afterOf(change: ScheduleChange): SchedulerTask = (change as ScheduleChange.UpdateTask).after

    @Test
    fun moveToADateKeepsTimeOfDayAndDuration() {
        val changes = ready(planner.plan(state, listOf(ScheduleCommand.MoveTask("implementation", LocalTarget(day.plusDays(1))))))
        val moved = afterOf(changes.single())
        assertEquals(at(day.plusDays(1), 11), moved.startMillis)
        assertEquals(at(day.plusDays(1), 14), moved.endMillis)
    }

    @Test
    fun moveToADateAndTime() {
        val changes = ready(planner.plan(state, listOf(ScheduleCommand.MoveTask("review", LocalTarget(day, LocalTime.of(8, 30))))))
        assertEquals(at(day, 8, 30), afterOf(changes.single()).startMillis)
        assertEquals(at(day, 9, 30), afterOf(changes.single()).endMillis)
    }

    @Test
    fun shiftExtendAndSetDuration() {
        val shifted = afterOf(ready(planner.plan(state, listOf(ScheduleCommand.ShiftTask("review", Amount(-1, AmountUnit.HOURS))))).single())
        assertEquals(at(day, 15) to at(day, 16), shifted.startMillis to shifted.endMillis)

        val extended = afterOf(ready(planner.plan(state, listOf(ScheduleCommand.ChangeDuration("review", Amount(2, AmountUnit.HOURS))))).single())
        assertEquals(at(day, 16) to at(day, 19), extended.startMillis to extended.endMillis)

        val set = afterOf(ready(planner.plan(state, listOf(ScheduleCommand.SetDuration("review", Amount(30, AmountUnit.MINUTES))))).single())
        assertEquals(at(day, 16) to at(day, 16, 30), set.startMillis to set.endMillis)
    }

    @Test
    fun shiftingByDaysKeepsLocalTimesAcrossADstChange() {
        // New York springs forward on Sun 8 Mar 2026: that day is 23 hours long.
        val ny = ZoneId.of("America/New_York")
        val sat = LocalDate.of(2026, 3, 7)
        val project = Project("p", "DST", sat, sat.plusDays(3))
        val task = SchedulerTask("t", "p", "Task", at(sat, 9, z = ny), at(sat, 10, z = ny))
        val plan = CommandPlanner(ny).plan(ScheduleState(project, listOf(task), emptyList()), listOf(ScheduleCommand.ShiftTask("t", Amount(1, AmountUnit.DAYS))))
        val moved = afterOf(ready(plan).single())
        assertEquals(at(sat.plusDays(1), 9, z = ny), moved.startMillis)
        assertEquals(23 * 3_600_000L, moved.startMillis - task.startMillis)
        assertEquals(task.durationMillis, moved.durationMillis)
    }

    @Test
    fun problemsNameTheCommandThatFailed() {
        val plan = planner.plan(
            state,
            listOf(
                ScheduleCommand.RenameTask("review", "Sign-off"),
                ScheduleCommand.ShiftTask("nope", Amount(1, AmountUnit.HOURS)),
            ),
        )
        assertEquals(CommandPlanner.Plan.Failed(1, CommandProblem.UnknownTask("nope")), plan)
    }

    @Test
    fun taskRulesAreApplied() {
        assertEquals(
            CommandPlanner.Plan.Failed(0, CommandProblem.InvalidTask("Review", TaskProblem.OutsideProject)),
            planner.plan(state, listOf(ScheduleCommand.ShiftTask("review", Amount(3, AmountUnit.DAYS)))),
        )
        assertEquals(
            CommandPlanner.Plan.Failed(0, CommandProblem.InvalidTask("Review", TaskProblem.EndNotAfterStart)),
            planner.plan(state, listOf(ScheduleCommand.SetTimes("review", start = null, end = day.atTime(15, 0)))),
        )
        assertEquals(
            CommandPlanner.Plan.Failed(0, CommandProblem.InvalidTask("", TaskProblem.BlankTitle)),
            planner.plan(state, listOf(ScheduleCommand.RenameTask("review", "  "))),
        )
        assertEquals(
            CommandPlanner.Plan.Failed(0, CommandProblem.InvalidTask("Review", TaskProblem.TooShort(15))),
            planner.plan(state, listOf(ScheduleCommand.ChangeDuration("review", Amount(-2, AmountUnit.HOURS)))),
        )
    }

    @Test
    fun createWithAPredecessor() {
        val changes = ready(
            planner.plan(
                state,
                listOf(ScheduleCommand.CreateTask(" Launch ", LocalDateTime.of(day.plusDays(1), LocalTime.of(10, 0)), LocalDateTime.of(day.plusDays(1), LocalTime.of(12, 0)), setOf("review"))),
            ),
        )
        val created = (changes[0] as ScheduleChange.CreateTask).task
        assertEquals("Launch", created.title)
        assertEquals(4, created.sortOrder)
        assertEquals(ScheduleChange.AddLink(TaskDependency("id2", SampleTasks.PROJECT_ID, "review", created.id)), changes[1])
    }

    @Test
    fun deleteAlsoRemovesTheTasksLinks() {
        val link = TaskDependency("l", SampleTasks.PROJECT_ID, "implementation", "qa-testing")
        val changes = ready(planner.plan(state.copy(links = listOf(link)), listOf(ScheduleCommand.DeleteTask("qa-testing"))))
        assertEquals(listOf(ScheduleChange.RemoveLink(link), ScheduleChange.DeleteTask(state.tasks[2])), changes)
    }

    @Test
    fun dependencyCommands() {
        val linked = state.copy(links = listOf(TaskDependency("l", SampleTasks.PROJECT_ID, "ui-design", "implementation")))
        assertEquals(
            CommandPlanner.Plan.Failed(0, CommandProblem.WouldCycle("implementation", "ui-design")),
            planner.plan(linked, listOf(ScheduleCommand.AddDependency("implementation", "ui-design"))),
        )
        assertEquals(
            CommandPlanner.Plan.Failed(0, CommandProblem.AlreadyLinked("ui-design", "implementation")),
            planner.plan(linked, listOf(ScheduleCommand.AddDependency("ui-design", "implementation"))),
        )
        assertEquals(
            CommandPlanner.Plan.Failed(0, CommandProblem.NotLinked("review", "implementation")),
            planner.plan(linked, listOf(ScheduleCommand.RemoveDependency("review", "implementation"))),
        )
        assertEquals(
            listOf(ScheduleChange.RemoveLink(linked.links.single())),
            ready(planner.plan(linked, listOf(ScheduleCommand.RemoveDependency("ui-design", "implementation")))),
        )
    }

    @Test
    fun laterCommandsSeeEarlierOnesAndEachTaskIsListedOnce() {
        val chained = state.copy(links = listOf(TaskDependency("l", SampleTasks.PROJECT_ID, "ui-design", "implementation")))
        val changes = ready(
            planner.plan(
                chained,
                listOf(
                    ScheduleCommand.ChangeDuration("ui-design", Amount(1, AmountUnit.HOURS)),
                    ScheduleCommand.ChangeDuration("ui-design", Amount(1, AmountUnit.HOURS)),
                    ScheduleCommand.RescheduleDependents("ui-design"),
                ),
            ),
        )
        assertEquals(2, changes.size)
        val design = changes[0] as ScheduleChange.UpdateTask
        assertEquals(state.tasks[0], design.before)
        assertEquals(at(day, 13), design.after.endMillis)
        assertEquals(at(day, 13) to at(day, 16), afterOf(changes[1]).let { it.startMillis to it.endMillis })
        assertNull(ScheduleChanges.problemWith(chained, changes, zone))
    }

    @Test
    fun changesThatCancelOutPlanNothing() {
        val changes = ready(
            planner.plan(
                state,
                listOf(ScheduleCommand.ShiftTask("review", Amount(1, AmountUnit.HOURS)), ScheduleCommand.ShiftTask("review", Amount(-60, AmountUnit.MINUTES))),
            ),
        )
        assertTrue(changes.isEmpty())
    }
}
