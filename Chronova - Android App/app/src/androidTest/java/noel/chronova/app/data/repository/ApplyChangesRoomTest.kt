package noel.chronova.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import noel.chronova.core.database.ChronovaDatabase
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.domain.schedule.ChangeProblem
import noel.chronova.core.domain.schedule.ScheduleChange
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import noel.chronova.core.data.repository.FirstLaunchFlag
import noel.chronova.core.data.repository.RoomProjectRepository

/** [RoomProjectRepository.applyChanges] against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class ApplyChangesRoomTest {

    private val zone = ZoneOffset.UTC
    private var now = 1_000L
    private lateinit var db: ChronovaDatabase
    private lateinit var repository: RoomProjectRepository
    private lateinit var apply: ApplyScheduleChanges

    private val tasks get() = SampleTasks.tasks(zone)
    private val link get() = TaskDependency("l1", SampleTasks.PROJECT_ID, tasks[0].id, tasks[1].id)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChronovaDatabase::class.java).build()
        val firstLaunch = object : FirstLaunchFlag {
            override fun isFirst() = true
            override fun markDone() = Unit
        }
        repository = RoomProjectRepository(db, firstLaunch, zone = { zone }, clock = { now })
        apply = ApplyScheduleChanges(repository, zone)
        repository.seedSampleIfFirstLaunch()
        repository.setPredecessors(SampleTasks.PROJECT_ID, tasks[1].id, setOf(tasks[0].id))
        // The seeded link gets a random id; replace it with a known one for the tests.
        val seeded = db.dependencyDao().live(SampleTasks.PROJECT_ID).single()
        db.dependencyDao().markDeleted(listOf(seeded.id), now)
        db.dependencyDao().upsertAll(listOf(seeded.copy(id = link.id, updatedAt = now)))
        // As if everything had been synced.
        db.syncDao().dirtyTasks().forEach { db.syncDao().markTaskClean(it.id, it.updatedAt) }
        db.syncDao().dirtyDependencies().forEach { db.syncDao().markDependencyClean(it.id, it.updatedAt) }
        now = 2_000L
    }

    @After
    fun tearDown() = db.close()

    private fun shifted(task: SchedulerTask) = task.copy(startMillis = task.startMillis + 3_600_000, endMillis = task.endMillis + 3_600_000)

    @Test
    fun appliedChangesAreStampedAndMarkedForSync() = runTest {
        val qa = tasks[2]
        val result = apply(SampleTasks.PROJECT_ID, listOf(ScheduleChange.UpdateTask(qa, shifted(qa))))
        assertTrue(result is ApplyResult.Applied)
        val row = db.syncDao().task(qa.id)!!
        assertEquals(shifted(qa).startMillis, row.startMillis)
        assertEquals(2_000L, row.updatedAt)
        assertTrue(row.dirty)
    }

    @Test
    fun aRejectedListWritesNothing() = runTest {
        val qa = tasks[2]
        val stale = tasks[3].copy(title = "Not what's stored")
        val result = apply(
            SampleTasks.PROJECT_ID,
            listOf(ScheduleChange.UpdateTask(qa, shifted(qa)), ScheduleChange.UpdateTask(stale, shifted(stale))),
        )
        assertEquals(ApplyResult.Rejected(ChangeProblem.Stale(setOf(stale.id))), result)
        assertEquals(tasks, repository.tasksOf(SampleTasks.PROJECT_ID))
        assertTrue(db.syncDao().dirtyTasks().isEmpty())
    }

    @Test
    fun deleteWithLinksAndUndoRestoreTheSameRows() = runTest {
        val design = tasks[0]
        val result = apply(SampleTasks.PROJECT_ID, listOf(ScheduleChange.RemoveLink(link), ScheduleChange.DeleteTask(design)))
        assertTrue(result is ApplyResult.Applied)
        assertFalse(repository.tasksOf(SampleTasks.PROJECT_ID).any { it.id == design.id })
        assertTrue(db.dependencyDao().live(SampleTasks.PROJECT_ID).isEmpty())
        assertTrue(db.syncDao().task(design.id)!!.deleted)

        now = 3_000L
        val undo = apply(SampleTasks.PROJECT_ID, (result as ApplyResult.Applied).undo)
        assertTrue(undo is ApplyResult.Applied)
        assertEquals(tasks, repository.tasksOf(SampleTasks.PROJECT_ID))
        assertEquals(listOf(link), db.dependencyDao().live(SampleTasks.PROJECT_ID).map { TaskDependency(it.id, it.projectId, it.predecessorId, it.successorId) })
        // Restored rows are new local edits, so the restore syncs too.
        val restored = db.syncDao().task(design.id)!!
        assertFalse(restored.deleted)
        assertTrue(restored.dirty)
        assertEquals(3_000L, restored.updatedAt)
    }
}
