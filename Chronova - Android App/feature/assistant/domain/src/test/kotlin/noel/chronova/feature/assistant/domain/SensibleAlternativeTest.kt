package noel.chronova.feature.assistant.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.domain.schedule.ScheduleState
import noel.chronova.core.domain.schedule.WorkingTime
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The walkthrough's café project as recorded on 14 Nov 2026 (working days 09:00–17:00; the 16th is a
 * Monday), and the alternatives the model offered there.
 */
class SensibleAlternativeTest {
    private val zone: ZoneId = ZoneId.of("Asia/Manila")
    private val check = SensibleAlternative(WorkingTime(zone))
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun task(id: String, from: Long, to: Long, milestone: Boolean = false) = SchedulerTask(id, "cafe", id, from, to, isMilestone = milestone)
    private fun link(from: String, to: String) = TaskDependency("$from>$to", "cafe", from, to)

    private val tasks = listOf(
        task("Design", at(16, 9), at(17, 17)),
        task("Build", at(18, 9), at(20, 17)),
        task("Equipment install", at(23, 9), at(24, 17)),
        task("Hire staff", at(16, 9), at(18, 17)),
        task("Train staff", at(25, 9), at(25, 17)),
        task("Menu tasting", at(26, 10), at(26, 13)),
        task("Soft opening", at(27, 9), at(27, 9), milestone = true),
    )
    private val state = ScheduleState(
        Project("cafe", "Cafe Opening", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 27)),
        tasks,
        listOf(
            link("Design", "Build"), link("Build", "Equipment install"), link("Equipment install", "Train staff"),
            link("Hire staff", "Train staff"), link("Train staff", "Soft opening"), link("Menu tasting", "Soft opening"),
        ),
    )
    private fun move(id: String, from: Long, to: Long): ScheduleChange.UpdateTask {
        val before = tasks.first { it.id == id }
        return ScheduleChange.UpdateTask(before, before.copy(startMillis = from, endMillis = to))
    }

    /** "Move menu tasting to Wednesday" (read as Wed 18), which overlaps Hire staff. */
    private val toWednesday18 = listOf(move("Menu tasting", at(18, 10), at(18, 13)))

    /** "Move menu tasting to Wednesday the 25th", which overlaps Train staff. */
    private val toWednesday25 = listOf(move("Menu tasting", at(25, 10), at(25, 13)))

    @Test
    fun movingATaskTheRequestDidNotTouchIsRefused() {
        // R7-take1: Hire staff, three days long, moved out of the way of a three-hour tasting.
        assertFalse(check.accepts(toWednesday18, toWednesday18 + move("Hire staff", at(19, 9), at(23, 17)), state))
        // R7-take2: Build and Equipment install moved for one clash.
        assertFalse(
            check.accepts(
                toWednesday25,
                listOf(move("Build", at(18, 9), at(20, 17)), move("Equipment install", at(20, 9), at(23, 17)), move("Menu tasting", at(25, 13), at(25, 16))),
                state,
            ),
        )
    }

    @Test
    fun aTimeOutsideWorkingHoursIsRefused() {
        // R7-take3: the tasting offered at 06:00–09:00.
        assertFalse(check.accepts(toWednesday25, listOf(move("Menu tasting", at(25, 6), at(25, 9))), state))
        assertFalse(check.accepts(toWednesday25, listOf(move("Menu tasting", at(25, 15), at(25, 18))), state))
        assertFalse(check.accepts(toWednesday25, listOf(move("Menu tasting", at(21, 10), at(21, 13))), state)) // a Saturday
        assertFalse(check.accepts(toWednesday25, listOf(move("Menu tasting", at(24, 17), at(25, 9))), state)) // overnight, no work in it
    }

    @Test
    fun theRequestsOwnTaskAtAnotherWorkingTimeIsAccepted() {
        assertTrue(check.accepts(toWednesday25, listOf(move("Menu tasting", at(26, 13), at(26, 16))), state))
        assertTrue(check.accepts(toWednesday25, listOf(move("Menu tasting", at(24, 14), at(25, 9))), state)) // across a night, in working time
    }

    @Test
    fun movingWhatWaitsForTheRequestsTaskIsAccepted() {
        // Equipment install pushed later; Train staff waits for it, so moving it too is fair.
        val later = listOf(move("Equipment install", at(24, 9), at(25, 17)))
        assertTrue(check.accepts(later, later + move("Train staff", at(26, 9), at(26, 17)), state))
        // Hire staff doesn't wait for Equipment install.
        assertFalse(check.accepts(later, later + move("Train staff", at(26, 9), at(26, 17)) + move("Hire staff", at(19, 9), at(23, 17)), state))
    }

    @Test
    fun outsideWorkingHoursIsFineWhenTheRequestWasOutsideThemToo() {
        // "Move menu tasting to Saturday": the user chose a day off, so another time that day is fine.
        val saturday = listOf(move("Menu tasting", at(21, 10), at(21, 13)))
        assertTrue(check.accepts(saturday, listOf(move("Menu tasting", at(21, 14), at(21, 17))), state))
        // Whole days (as imported from a chart) have no working hours to keep.
        val wholeDay = listOf(move("Menu tasting", at(25, 0), at(26, 0)))
        assertTrue(check.accepts(wholeDay, listOf(move("Menu tasting", at(26, 0), at(27, 0))), state))
    }

    @Test
    fun aNewTaskIsMatchedByItsTitle() {
        val added = SchedulerTask("new-1", "cafe", "Photo shoot", at(25, 10), at(25, 12))
        val planned = listOf(ScheduleChange.CreateTask(added))
        assertTrue(check.accepts(planned, listOf(ScheduleChange.CreateTask(added.copy(id = "new-2", startMillis = at(26, 14), endMillis = at(26, 16)))), state))
        assertFalse(check.accepts(planned, listOf(ScheduleChange.CreateTask(added.copy(id = "new-2", startMillis = at(26, 6), endMillis = at(26, 8)))), state))
        // A task the request didn't ask for.
        assertFalse(check.accepts(planned, planned + ScheduleChange.CreateTask(added.copy(id = "new-3", title = "Extra")), state))
    }

    @Test
    fun deletingATaskTheRequestKeptIsRefused() {
        assertFalse(check.accepts(toWednesday25, toWednesday25 + ScheduleChange.DeleteTask(tasks.first { it.id == "Train staff" }), state))
    }
}
