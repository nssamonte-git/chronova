package noel.chronova.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import noel.chronova.core.database.ChronovaDatabase
import noel.chronova.core.domain.SampleTasks
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TimeScale
import noel.chronova.core.model.TimelineConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [RoomProjectRepository] against a real (in-memory) Room database; separate from the app's data. */
@RunWith(AndroidJUnit4::class)
class RoomProjectRepositoryTest {
    private var now = 1_000L
    private var ids = 0
    private var first = true
    private lateinit var db: ChronovaDatabase
    private lateinit var repository: RoomProjectRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChronovaDatabase::class.java).build()
        val flag = object : FirstLaunchFlag {
            override fun isFirst() = first
            override fun markDone() {
                first = false
            }
        }
        repository = RoomProjectRepository(db, flag, zone = { ZoneOffset.UTC }, clock = { now }, newId = { "id-${++ids}" })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun newProject() = repository.createProject("Launch", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 8))

    @Test
    fun everyWriteIsStampedAndMarkedForSync() = runTest {
        val projectId = newProject()
        now = 2_000L
        val first = repository.createTask(projectId, "Design", 0L, 3_600_000L, TaskColor.BLUE)
        val second = repository.createTask(projectId, "Build", 0L, 3_600_000L, TaskColor.GREEN)

        assertEquals(listOf(first, second), repository.observeTasks(projectId).first().map { it.id })
        assertEquals(listOf(0, 1), repository.observeTasks(projectId).first().map { it.sortOrder })
        val dirtyTasks = db.syncDao().dirtyTasks()
        assertEquals(setOf(first, second), dirtyTasks.map { it.id }.toSet())
        assertTrue(dirtyTasks.all { it.updatedAt == 2_000L })
        assertEquals(1_000L, db.syncDao().dirtyProjects().single().updatedAt)
    }

    @Test
    fun aCopiedProjectIsWrittenWholeOrNotAtAll() = runTest {
        val project = Project("copy", "Copy", LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 11))
        val design = SchedulerTask("t1", "copy", "Design", 0L, 3_600_000L, notes = "Kept")
        val build = SchedulerTask("t2", "copy", "Build", 3_600_000L, 7_200_000L, sortOrder = 1)
        repository.createProjectWithSchedule(project, listOf(design, build), listOf(TaskDependency("l1", "copy", "t1", "t2")))

        assertEquals(project, repository.observeProject("copy").first())
        assertEquals(listOf(design, build), repository.observeTasks("copy").first())
        assertEquals(listOf("l1"), repository.observeDependencies("copy").first().map { it.id })
        assertEquals(3, db.syncDao().dirtyTasks().size + db.syncDao().dirtyDependencies().size)

        // A task pointing at a project that doesn't exist breaks the foreign key: nothing is kept.
        val broken = SchedulerTask("t3", "nowhere", "Lost", 0L, 3_600_000L)
        val failed = runCatching {
            repository.createProjectWithSchedule(project.copy(id = "copy2"), listOf(broken), emptyList())
        }
        assertTrue(failed.isFailure)
        assertNull(repository.observeProject("copy2").first())
    }

    @Test
    fun deletingAProjectTombstonesItsTasksAndLinks() = runTest {
        val projectId = newProject()
        val design = repository.createTask(projectId, "Design", 0L, 3_600_000L, TaskColor.BLUE)
        val build = repository.createTask(projectId, "Build", 3_600_000L, 7_200_000L, TaskColor.GREEN)
        repository.setPredecessors(projectId, build, setOf(design))
        now = 3_000L

        repository.deleteProject(projectId)

        assertNull(repository.observeProject(projectId).first())
        assertTrue(repository.observeProjects().first().isEmpty())
        assertTrue(db.syncDao().dirtyProjects().single().deleted)
        assertTrue(db.syncDao().dirtyTasks().all { it.deleted && it.updatedAt == 3_000L })
        assertTrue(db.syncDao().dirtyDependencies().all { it.deleted })
    }

    @Test
    fun predecessorsAreMadeExactlyTheGivenSet() = runTest {
        val projectId = newProject()
        val a = repository.createTask(projectId, "A", 0L, 3_600_000L, TaskColor.BLUE)
        val b = repository.createTask(projectId, "B", 0L, 3_600_000L, TaskColor.BLUE)
        val c = repository.createTask(projectId, "C", 0L, 3_600_000L, TaskColor.BLUE)

        repository.setPredecessors(projectId, c, setOf(a, b))
        assertEquals(setOf(a, b), repository.observeDependencies(projectId).first().map { it.predecessorId }.toSet())

        repository.setPredecessors(projectId, c, setOf(b))
        assertEquals(listOf(b), repository.observeDependencies(projectId).first().map { it.predecessorId })
    }

    @Test
    fun viewSettingsAreRememberedPerProject() = runTest {
        val projectId = newProject()
        assertNull(repository.viewSettings(projectId))
        val config = TimelineConfig().withTimeScale(TimeScale.DAYS)
        repository.saveViewSettings(projectId, config)
        assertEquals(config, repository.viewSettings(projectId))
        val withPath = config.copy(showCriticalPath = true)
        repository.saveViewSettings(projectId, withPath)
        assertEquals(withPath, repository.viewSettings(projectId))
    }

    @Test
    fun theSampleIsSeededOnlyOnTheFirstLaunch() = runTest {
        repository.seedSampleIfFirstLaunch()
        repository.seedSampleIfFirstLaunch()
        assertEquals(listOf(SampleTasks.PROJECT_ID), repository.observeProjects().first().map { it.project.id })
        assertEquals(SampleTasks.tasks(ZoneOffset.UTC).size, repository.tasksOf(SampleTasks.PROJECT_ID).size)

        repository.deleteProject(SampleTasks.PROJECT_ID)
        repository.seedSampleIfFirstLaunch()
        assertTrue(repository.observeProjects().first().isEmpty()) // deleting it doesn't bring it back
    }
}
