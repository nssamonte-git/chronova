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
