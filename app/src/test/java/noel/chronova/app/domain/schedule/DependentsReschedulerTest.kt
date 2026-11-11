package noel.chronova.app.domain.schedule

import java.time.LocalDate
import java.time.ZoneOffset
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskDependency
import org.junit.Assert.assertEquals
import org.junit.Test

class DependentsReschedulerTest {

    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 3, 2)
    private val project = Project("p", "P", day, day) // one day: 00:00–24:00
    private val window = project.window(zone)

    private fun at(hour: Int) = day.atTime(hour, 0).toInstant(zone).toEpochMilli()
    private fun task(id: String, from: Int, to: Int) = SchedulerTask(id, "p", id.uppercase(), at(from), at(to))
    private fun link(p: String, s: String) = TaskDependency("$p>$s", "p", p, s)

    private fun moves(state: ScheduleState, taskId: String): Map<String, Pair<Int, Int>> {
        val result = DependentsRescheduler.reschedule(state, taskId, window) as DependentsRescheduler.Result.Moves
        return result.updates.associate { it.after.id to (hourOf(it.after.startMillis) to hourOf(it.after.endMillis)) }
    }

    private fun hourOf(millis: Long) = ((millis - at(0)) / 3_600_000L).toInt()

    @Test
    fun aChainIsPushedJustEnoughKeepingDurations() {
        // A ran late (now ends at 12); B, C, D follow in a chain.
        val state = ScheduleState(
            project,
            listOf(task("a", 9, 12), task("b", 11, 13), task("c", 13, 14), task("d", 16, 17)),
            listOf(link("a", "b"), link("b", "c"), link("c", "d")),
        )
        // B 12–14, C 14–15; D already starts after C ends, so it stays.
        assertEquals(mapOf("b" to (12 to 14), "c" to (14 to 15)), moves(state, "a"))
    }

    @Test
    fun aTaskWithTwoPredecessorsWaitsForTheLaterOne() {
        // A → B → D and A → C → D; C is longer, so D waits for C.
        val state = ScheduleState(
            project,
            listOf(task("a", 8, 10), task("b", 9, 10), task("c", 9, 13), task("d", 11, 12)),
            listOf(link("a", "b"), link("a", "c"), link("b", "d"), link("c", "d")),
        )
        assertEquals(mapOf("b" to (10 to 11), "c" to (10 to 14), "d" to (14 to 15)), moves(state, "a"))
    }

    @Test
    fun predecessorsOutsideTheChainCountToo() {
        // D depends on A (pushed) and on X (unrelated, ends at 15).
        val state = ScheduleState(
            project,
            listOf(task("a", 8, 12), task("x", 9, 15), task("d", 11, 12)),
            listOf(link("a", "d"), link("x", "d")),
        )
        assertEquals(mapOf("d" to (15 to 16)), moves(state, "a"))
    }

    @Test
    fun nothingMovesEarlierAndUnrelatedTasksStay() {
        val state = ScheduleState(
            project,
            listOf(task("a", 8, 9), task("b", 12, 13), task("z", 1, 2)),
            listOf(link("a", "b")),
        )
        assertEquals(emptyMap<String, Pair<Int, Int>>(), moves(state, "a"))
    }

    @Test
    fun pushesPastTheProjectEndAreRefused() {
        val state = ScheduleState(
            project,
            listOf(task("a", 9, 22), task("b", 10, 13)),
            listOf(link("a", "b")),
        )
        assertEquals(DependentsRescheduler.Result.DoesNotFit(listOf("b")), DependentsRescheduler.reschedule(state, "a", window))
    }

    @Test
    fun aCycleFromASyncDoesNotHang() {
        val state = ScheduleState(
            project,
            listOf(task("a", 8, 12), task("b", 9, 10), task("c", 9, 10)),
            listOf(link("a", "b"), link("b", "c"), link("c", "b")),
        )
        // B and C wait on each other, so neither can be ordered; they're left alone.
        assertEquals(emptyMap<String, Pair<Int, Int>>(), moves(state, "a"))
    }
}
