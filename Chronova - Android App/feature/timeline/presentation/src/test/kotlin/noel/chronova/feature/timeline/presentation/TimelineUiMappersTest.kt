package noel.chronova.feature.timeline.presentation

import noel.chronova.core.domain.TaskProblem
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineUiMappersTest {
    private val build = SchedulerTask("build", "p", "Build", startMillis = 1_000L, endMillis = 5_000L, color = TaskColor.GREEN, sortOrder = 2)
    private val test = SchedulerTask("test", "p", "Test", startMillis = 5_000L, endMillis = 9_000L)

    @Test
    fun aTaskKeepsWhatItsRowAndBarShow() {
        assertEquals(TaskUi("build", "Build", 1_000L, 5_000L, TaskColor.GREEN), build.toUi())
    }

    @Test
    fun aLinkIsViolatedWhenTheSuccessorStartsBeforeThePredecessorEnds() {
        val link = TaskDependency("l", "p", predecessorId = "build", successorId = "test")
        assertFalse(link.toUi(mapOf("build" to build, "test" to test)).violated)
        val early = test.copy(startMillis = 4_000L)
        assertTrue(link.toUi(mapOf("build" to build, "test" to early)).violated)
        // A link to a task that isn't loaded isn't drawn as broken.
        assertEquals(DependencyUi("build", "test", violated = false), link.toUi(mapOf("build" to build)))
    }

    @Test
    fun everyProblemKeepsItsDetail() {
        assertEquals(TaskProblemUi.BlankTitle, TaskProblem.BlankTitle.toUi())
        assertEquals(TaskProblemUi.EndNotAfterStart, TaskProblem.EndNotAfterStart.toUi())
        assertEquals(TaskProblemUi.TooShort(15), TaskProblem.TooShort(15).toUi())
        assertEquals(TaskProblemUi.OutsideProject, TaskProblem.OutsideProject.toUi())
    }

    @Test
    fun aDraggedSpanReplacesOnlyTheTimes() {
        val ui = build.toUi()
        val moved = ui.withSpan(noel.chronova.core.timeline.TimeSpan(2_000L, 6_000L))
        assertEquals(ui.copy(startMillis = 2_000L, endMillis = 6_000L), moved)
        assertEquals(4_000L, moved.durationMillis)
    }
}
