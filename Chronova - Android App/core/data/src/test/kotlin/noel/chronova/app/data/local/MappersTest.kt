package noel.chronova.app.data.local

import java.time.LocalDate
import noel.chronova.app.domain.model.Project
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TaskColor
import noel.chronova.app.domain.model.TaskDependency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(project, project.toEntity(now = 1L).toDomain())
        val link = TaskDependency("l1", "p1", "t0", "t1")
        assertEquals(link, link.toEntity(now = 1L).toDomain())
    }

    @Test
    fun anUnknownColourFromANewerVersionFallsBackToBlue() {
        assertEquals(TaskColor.BLUE, task.toEntity(now = 1L).copy(color = "TEAL").toDomain().color)
    }
}
