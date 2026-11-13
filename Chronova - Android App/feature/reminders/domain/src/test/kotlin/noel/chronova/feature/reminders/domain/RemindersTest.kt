package noel.chronova.feature.reminders.domain

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import noel.chronova.core.model.Project
import noel.chronova.core.model.ReminderLead
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.testing.FakeProjectRepository
import noel.chronova.core.testing.FakeReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val now = 1_000 * hour

    private val project = Project("p1", "Office move", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6))
    private val build = SchedulerTask("b", "p1", "Build", now + 2 * hour, now + 3 * hour)
    private val launch = SchedulerTask("l", "p1", "Launch", now + 5 * hour, now + 5 * hour, isMilestone = true)
    private val done = SchedulerTask("d", "p1", "Design", now + hour, now + 2 * hour, progress = 100)
    private val names = mapOf("p1" to "Office move")

    @Test
    fun eachLeadPutsTheReminderThatLongBeforeTheStartAndOffHasNone() {
        assertEquals(emptyList<Reminder>(), ReminderPlanner.reminders(listOf(build), names, ReminderLead.OFF))
        val expected = mapOf(
            ReminderLead.AT_START to now + 2 * hour,
            ReminderLead.MINUTES_15 to now + 2 * hour - 15 * minute,
            ReminderLead.HOUR_1 to now + hour,
            ReminderLead.DAY_1 to now + 2 * hour - 24 * hour,
        )
        for ((lead, at) in expected) assertEquals(lead.name, at, ReminderPlanner.reminders(listOf(build), names, lead).single().atMillis)
        val reminder = ReminderPlanner.reminders(listOf(launch), names, ReminderLead.AT_START).single()
        assertEquals(Reminder("l", "p1", "Office move", "Launch", true, now + 5 * hour, now + 5 * hour), reminder)
    }

    @Test
    fun doneTasksHaveNoReminder() {
        assertEquals(listOf("b"), ReminderPlanner.reminders(listOf(build, done), names, ReminderLead.AT_START).map { it.taskId })
    }

    @Test
    fun dueIsWhatCameSinceTheLastTimeAndNotTooLongAgo() {
        val reminders = ReminderPlanner.reminders(listOf(build, launch), names, ReminderLead.AT_START)
        assertEquals(listOf("b"), ReminderPlanner.due(reminders, sinceMillis = now, nowMillis = now + 2 * hour).map { it.taskId })
        // Already handled at that very moment: not again.
        assertEquals(emptyList<Reminder>(), ReminderPlanner.due(reminders, sinceMillis = now + 2 * hour, nowMillis = now + 3 * hour))
        // More than a day late: dropped.
        assertEquals(listOf("l"), ReminderPlanner.due(reminders, sinceMillis = now, nowMillis = now + 27 * hour).map { it.taskId })
        assertEquals(now + 2 * hour, ReminderPlanner.next(reminders, now))
        assertEquals(now + 5 * hour, ReminderPlanner.next(reminders, now + 2 * hour))
        assertNull(ReminderPlanner.next(reminders, now + 5 * hour))
    }

    private inner class Harness(lead: ReminderLead = ReminderLead.AT_START, tasks: List<SchedulerTask> = listOf(build, launch, done)) {
        var clock = now
        var signedIn = true
        val alarms = mutableListOf<Long?>()
        val posted = mutableListOf<List<String>>()
        val settings = FakeReminderSettings(lead)
        val repository = FakeProjectRepository(listOf(project), tasks)
        val log = object : ReminderLog {
            override var handledUpToMillis = 0L
        }
        val engine = ReminderEngine(
            repository, settings, signedIn = { signedIn },
            alarm = { alarms += it }, poster = { list -> posted += list.map { it.title } }, log = log, clock = { clock },
        )
    }

    @Test
    fun rescheduleSetsTheNextAlarmAndPostsNothing() = runTest {
        val h = Harness()
        h.engine.reschedule()
        assertEquals(listOf<Long?>(now + 2 * hour), h.alarms)
        assertTrue(h.posted.isEmpty())
        assertEquals(now, h.log.handledUpToMillis)
    }

    @Test
    fun theAlarmPostsWhatIsDueEvenWhenLateAndSetsTheNext() = runTest {
        val h = Harness()
        h.engine.reschedule()
        h.clock = now + 2 * hour + 7 * minute // the phone held the alarm back a few minutes
        h.engine.onAlarm()
        assertEquals(listOf(listOf("Build")), h.posted)
        assertEquals(now + 5 * hour, h.alarms.last())
        h.engine.onAlarm() // a second wake-up posts nothing twice
        assertEquals(1, h.posted.size)
    }

    @Test
    fun aTaskAddedAfterItsReminderTimeIsNotRemindedLate() = runTest {
        val h = Harness(lead = ReminderLead.MINUTES_15)
        h.engine.reschedule()
        // A task starting in 5 minutes is added: its reminder time (10 minutes ago) has passed.
        h.clock = now + 30 * minute
        h.repository.tasks.value = h.repository.tasks.value + SchedulerTask("s", "p1", "Stand-up", now + 35 * minute, now + 50 * minute)
        h.engine.reschedule()
        h.clock = now + 2 * hour - 15 * minute
        h.engine.onAlarm()
        assertEquals(listOf(listOf("Build")), h.posted)
    }

    @Test
    fun signedOutOrOffMeansNoAlarm() = runTest {
        val off = Harness(lead = ReminderLead.OFF)
        off.engine.reschedule()
        assertEquals(listOf<Long?>(null), off.alarms)
        val out = Harness().apply { signedIn = false }
        out.engine.reschedule()
        out.clock = now + 3 * hour
        out.engine.onAlarm()
        assertEquals(listOf<Long?>(null, null), out.alarms)
        assertTrue(out.posted.isEmpty())
    }
}
