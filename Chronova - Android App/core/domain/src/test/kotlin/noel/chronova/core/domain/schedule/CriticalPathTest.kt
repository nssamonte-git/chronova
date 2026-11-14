package noel.chronova.core.domain.schedule

import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalPathTest {
    private val hour = 3_600_000L
    private fun task(id: String, from: Int, to: Int) = SchedulerTask(id, "p", id, from * hour, to * hour)
    private fun milestone(id: String, at: Int) = SchedulerTask(id, "p", id, at * hour, at * hour, isMilestone = true)
    private fun link(from: String, to: String) = TaskDependency("$from>$to", "p", from, to)

    @Test
    fun aChainIsCriticalAndAParallelBranchHasSlack() {
        // design 0–2 → build 2–6 → ship 6–7; docs 2–4 also waits for design and nothing waits for docs.
        val tasks = listOf(task("design", 0, 2), task("build", 2, 6), task("ship", 6, 7), task("docs", 2, 4))
        val result = CriticalPath.of(tasks, listOf(link("design", "build"), link("build", "ship"), link("design", "docs")))
        assertEquals(7 * hour, result.finishMillis)
        assertEquals(listOf("design", "build", "ship"), tasks.map { it.id }.filter(result::isCritical))
        assertEquals(3 * hour, result.slackMillis["docs"]) // it could end at 7 instead of 4
    }

    @Test
    fun aGapBeforeTheNextTaskIsSlack() {
        // a 0–1 → b 3–4: a could end as late as 3.
        val result = CriticalPath.of(listOf(task("a", 0, 1), task("b", 3, 4)), listOf(link("a", "b")))
        assertEquals(2 * hour, result.slackMillis["a"])
        assertFalse(result.isCritical("a"))
        assertTrue(result.isCritical("b"))
    }

    @Test
    fun unlinkedTasksAreMeasuredAgainstTheFinish() {
        val result = CriticalPath.of(listOf(task("a", 0, 2), task("b", 1, 5)), emptyList())
        assertEquals(3 * hour, result.slackMillis["a"])
        assertTrue(result.isCritical("b"))
    }

    @Test
    fun aMilestoneAtTheEndIsCriticalAndSoIsWhatItWaitsFor() {
        val result = CriticalPath.of(listOf(task("build", 0, 4), milestone("launch", 4), task("tidy", 0, 1)), listOf(link("build", "launch")))
        assertTrue(result.isCritical("launch"))
        assertTrue(result.isCritical("build"))
        assertEquals(3 * hour, result.slackMillis["tidy"])
    }

    @Test
    fun aBrokenLinkGivesNegativeSlack() {
        // b (1–2) starts before a (0–3) ends; the finish is 3, so b may start by 2 and a should end by 2.
        val result = CriticalPath.of(listOf(task("a", 0, 3), task("b", 1, 2)), listOf(link("a", "b")))
        assertEquals(-1 * hour, result.slackMillis["a"])
        assertTrue(result.isCritical("a"))
    }

    // Working time: 09:00 to 17:00, Monday to Friday. November 2026: the 16th is a Monday.
    private val zone = java.time.ZoneId.of("Asia/Manila")
    private val working = WorkingTime(zone)
    private fun at(day: Int, hour: Int) = java.time.LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun work(id: String, from: Long, to: Long) = SchedulerTask(id, "p", id, from, to)

    /** The walkthrough's project: tasks run 09:00 to 17:00 and each starts the working morning after the one before ends. */
    private fun cafe(openingDay: Int): List<SchedulerTask> = listOf(
        work("design", at(16, 9), at(17, 17)),
        work("build", at(18, 9), at(20, 17)), // ends Friday
        work("equipment", at(23, 9), at(24, 17)), // starts Monday
        work("hire", at(16, 9), at(18, 17)),
        work("train", at(25, 9), at(25, 17)),
        SchedulerTask("opening", "p", "opening", at(openingDay, 9), at(openingDay, 9), isMilestone = true),
    )
    private val cafeLinks = listOf(link("design", "build"), link("build", "equipment"), link("equipment", "train"), link("hire", "train"), link("train", "opening"))

    @Test
    fun aChainThatIsBackToBackInWorkingTimeIsCritical() {
        // The opening is the morning after Train staff: no working time anywhere along the chain.
        val tasks = cafe(openingDay = 26)
        val result = CriticalPath.of(tasks, cafeLinks, working)
        assertEquals(listOf("design", "build", "equipment", "train", "opening"), tasks.map { it.id }.filter(result::isCritical))
        // Hire staff ends Wed 18 17:00 and Train staff starts Wed 25 09:00: Thu, Fri, Mon and Tue are free.
        assertEquals(32 * hour, result.slackMillis["hire"])
        assertEquals(tasks.map { it.id }.toSet(), result.inWorkingTime)
        assertEquals(at(26, 9), result.finishMillis)
    }

    @Test
    fun aFreeWorkingDayBeforeTheFinishIsSlackForTheWholeChain() {
        // As recorded: the opening is on Friday and Thursday is free, so the chain can slip a working day.
        val tasks = cafe(openingDay = 27) + work("tasting", at(26, 10), at(26, 13))
        val result = CriticalPath.of(tasks, cafeLinks + link("tasting", "opening"), working)
        assertEquals(listOf("opening"), tasks.map { it.id }.filter(result::isCritical))
        assertEquals(listOf(8 * hour, 8 * hour, 8 * hour, 8 * hour), listOf("design", "build", "equipment", "train").map { result.slackMillis[it] })
        assertEquals(4 * hour, result.slackMillis["tasting"]) // Thursday 13:00 to 17:00
    }

    @Test
    fun withoutWorkingHoursNightsStillCountAsBefore() {
        val tasks = cafe(openingDay = 26)
        val result = CriticalPath.of(tasks, cafeLinks)
        assertEquals(listOf("opening"), tasks.map { it.id }.filter(result::isCritical))
        assertEquals(16 * hour, result.slackMillis["train"]) // Wed 17:00 to Thu 09:00, by the clock
        assertEquals(emptySet<String>(), result.inWorkingTime)
    }

    @Test
    fun workOutsideWorkingHoursIsMeasuredByTheClock() {
        // A Saturday (21 Nov): prepare 09–12 → serve 13–17, and a delivery 09–10 nothing waits for.
        val saturday = listOf(work("prepare", at(21, 9), at(21, 12)), work("serve", at(21, 13), at(21, 17)), work("delivery", at(21, 9), at(21, 10)))
        val result = CriticalPath.of(saturday, listOf(link("prepare", "serve")), working)
        assertEquals(listOf("serve"), saturday.map { it.id }.filter(result::isCritical))
        assertEquals(1 * hour, result.slackMillis["prepare"])
        assertEquals(7 * hour, result.slackMillis["delivery"])
        assertEquals(emptySet<String>(), result.inWorkingTime)
    }

    @Test
    fun weekdayWorkAndWeekendWorkEachUseTheirOwnMeasure() {
        // Fit out Fri 09–17 → clean Sat 10–12 → open Mon 09–17; an evening inventory Fri 18–20 nothing waits for.
        val tasks = listOf(
            work("fit-out", at(20, 9), at(20, 17)), work("clean", at(21, 10), at(21, 12)),
            work("open", at(23, 9), at(23, 17)), work("inventory", at(20, 18), at(20, 20)),
        )
        val result = CriticalPath.of(tasks, listOf(link("fit-out", "clean"), link("clean", "open")), working)
        assertEquals(setOf("fit-out", "open"), result.inWorkingTime)
        assertTrue(result.isCritical("fit-out")) // no working time before the cleaning it is needed for
        assertTrue(result.isCritical("open"))
        assertEquals(45 * hour, result.slackMillis["clean"]) // Sat 12:00 to Mon 09:00, by the clock
        assertFalse(result.isCritical("inventory"))
    }

    @Test
    fun aMilestoneCountsAsWorkWhenItStandsInWorkingHours() {
        val closing = SchedulerTask("closing", "p", "closing", at(20, 17), at(20, 17), isMilestone = true) // Friday 17:00
        val party = SchedulerTask("party", "p", "party", at(21, 19), at(21, 19), isMilestone = true) // Saturday evening
        val result = CriticalPath.of(listOf(work("pack", at(20, 9), at(20, 17)), closing, party), listOf(link("pack", "closing")), working)
        assertEquals(setOf("pack", "closing"), result.inWorkingTime)
        assertTrue(result.isCritical("party"))
        // Nothing but an evening and a Saturday separates them from the finish.
        assertTrue(result.isCritical("closing"))
        assertTrue(result.isCritical("pack"))
    }

    @Test
    fun noTasksAndOneTask() {
        assertNull(CriticalPath.of(emptyList(), emptyList()).finishMillis)
        assertTrue(CriticalPath.of(listOf(task("a", 0, 1)), emptyList()).isCritical("a"))
    }

    @Test
    fun aCycleIsIgnoredRatherThanLooping() {
        val result = CriticalPath.of(listOf(task("a", 0, 1), task("b", 1, 2)), listOf(link("a", "b"), link("b", "a")))
        assertEquals(setOf("a", "b"), result.slackMillis.keys)
    }
}
