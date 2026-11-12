package noel.chronova.app.domain.voice

import java.time.LocalDate
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskDependency
import noel.chronova.app.domain.schedule.ScheduleChange
import noel.chronova.app.domain.schedule.ScheduleState
import noel.chronova.app.domain.voice.ScheduleWarning.BrokenLink
import noel.chronova.app.domain.voice.ScheduleWarning.Overlap
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleWarningTest {
    private val hour = 3_600_000L

    private fun task(id: String, title: String, from: Int, to: Int, order: Int = 0) =
        SchedulerTask(id, "p", title, from * hour, to * hour, sortOrder = order)

    private val build = task("b", "Build", 16, 18, 0)
    private val design = task("d", "Design", 15, 16, 1)
    private val test = task("t", "Test", 15, 16, 2)

    private fun state(vararg tasks: SchedulerTask, links: List<TaskDependency> = emptyList()) =
        ScheduleState(Project("p", "Voice test", LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 7)), tasks.toList(), links)

    private fun move(task: SchedulerTask, from: Int, to: Int) = ScheduleChange.UpdateTask(task, task.copy(startMillis = from * hour, endMillis = to * hour))

    private fun link(from: SchedulerTask, to: SchedulerTask) = TaskDependency("${from.id}>${to.id}", "p", from.id, to.id)

    @Test
    fun movingOntoAnotherTaskWarns() {
        assertEquals(listOf(Overlap("Test", "Build")), ScheduleWarning.from(listOf(move(test, 16, 17)), state(build, design, test)))
    }

    @Test
    fun aNewTaskThatOverlapsWarnsAboutEachTaskItMeets() {
        val review = task("r", "Review", 15, 17)
        assertEquals(
            listOf(Overlap("Review", "Design"), Overlap("Review", "Test"), Overlap("Review", "Build")),
            ScheduleWarning.from(listOf(ScheduleChange.CreateTask(review)), state(build, design, test)),
        )
    }

    @Test
    fun anOverlapThatWasAlreadyThereIsNotRepeated() {
        // Design and Test already share 15:00–16:00; nudging Test keeps that overlap but makes no new one.
        assertEquals(emptyList<ScheduleWarning>(), ScheduleWarning.from(listOf(move(test, 14, 16)), state(build, design, test)))
    }

    @Test
    fun aRenameOrTasksThatOnlyTouchAreNotClashes() {
        val rename = ScheduleChange.UpdateTask(design, design.copy(title = "Sketch"))
        assertEquals(emptyList<ScheduleWarning>(), ScheduleWarning.from(listOf(rename), state(build, design)))
        // Design 15–16 ends exactly as Build starts.
        assertEquals(emptyList<ScheduleWarning>(), ScheduleWarning.from(listOf(move(design, 14, 16)), state(build, design)))
    }

    @Test
    fun aSwapWithoutClashesIsQuietAndTwoMovedTasksMeetingAreReportedOnce() {
        val swap = listOf(move(design, 16, 17), move(build, 9, 11))
        assertEquals(emptyList<ScheduleWarning>(), ScheduleWarning.from(swap, state(build, design)))
        val collide = listOf(move(design, 9, 10), move(build, 9, 11))
        assertEquals(listOf(Overlap("Build", "Design")), ScheduleWarning.from(collide, state(build, design))) // same start: row order
    }

    @Test
    fun aLinkAddedAgainstTheCurrentTimesWarns() {
        // Build (16–18) is to wait for a Design that ends at 17.
        val late = design.copy(startMillis = 16 * hour, endMillis = 17 * hour)
        assertEquals(listOf(BrokenLink("Design", "Build")), ScheduleWarning.from(listOf(ScheduleChange.AddLink(link(late, build))), state(build, late)))
    }

    @Test
    fun movingATaskThatBreaksALinkWarnsOnceButALinkAlreadyBrokenDoesNot() {
        // Build now also overlaps Design, but the broken link says it.
        val links = listOf(link(design, build))
        assertEquals(listOf(BrokenLink("Design", "Build")), ScheduleWarning.from(listOf(move(build, 15, 17)), state(build, design, links = links)))

        val alreadyBroken = listOf(link(build, design)) // Design starts before Build ends
        assertEquals(emptyList<ScheduleWarning>(), ScheduleWarning.from(listOf(move(design, 14, 15)), state(build, design, links = alreadyBroken)))
    }

    @Test
    fun deletedTasksAndRemovedLinksAreLeftOut() {
        val links = listOf(link(design, build))
        val changes = listOf(ScheduleChange.RemoveLink(links[0]), ScheduleChange.DeleteTask(design), move(build, 15, 16))
        assertEquals(listOf(Overlap("Build", "Test")), ScheduleWarning.from(changes, state(build, design, test, links = links)))
    }
}
