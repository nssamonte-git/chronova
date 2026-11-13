package noel.chronova.core.data.local

import java.time.LocalDate
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {
    private val project = Project("p1", "Launch", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 8))
    private val task = SchedulerTask("t1", "p1", "Build", 1_000L, 5_000L, TaskColor.PURPLE, 2)

    @Test
    fun aLocalEditIsStampedAndMarkedForSync() {
        val entity = task.toEntity(now = 42L)
        assertEquals(42L, entity.updatedAt)
        assertTrue(entity.dirty)
        assertFalse(entity.deleted)
        assertEquals("PURPLE", entity.color)
        val projectEntity = project.toEntity(now = 7L)
        assertEquals("2026-03-02", projectEntity.startDate)
        assertTrue(projectEntity.dirty)
    }

    @Test
    fun recordsSurviveARoundTrip() {
        assertEquals(task, task.toEntity(now = 1L).toDomain())
        val tracked = task.copy(progress = 60, notes = "Waiting on review")
        assertEquals(tracked, tracked.toEntity(now = 1L).toDomain())
        val milestone = task.copy(endMillis = task.startMillis, progress = 100, isMilestone = true)
        assertEquals(milestone, milestone.toEntity(now = 1L).toDomain())
        assertEquals(project, project.toEntity(now = 1L).toDomain())
        val link = TaskDependency("l1", "p1", "t0", "t1")
        assertEquals(link, link.toEntity(now = 1L).toDomain())
    }

    @Test
    fun anUnknownColourFromANewerVersionFallsBackToBlue() {
        assertEquals(TaskColor.BLUE, task.toEntity(now = 1L).copy(color = "TEAL").toDomain()?.color)
    }

    @Test
    fun aRowThatBreaksTheTaskRulesIsLeftOut() {
        val entity = task.toEntity(now = 1L)
        assertNull(entity.copy(endMillis = entity.startMillis).toDomain()) // zero length, not a milestone
        assertNull(entity.copy(progress = 101).toDomain())
        assertNull(entity.copy(isMilestone = true).toDomain()) // a milestone with a length
    }
}
