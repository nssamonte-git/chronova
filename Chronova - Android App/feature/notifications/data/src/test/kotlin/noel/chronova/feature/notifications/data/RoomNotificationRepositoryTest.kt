package noel.chronova.feature.notifications.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import noel.chronova.core.domain.notifications.AppNotification
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.domain.schedule.ScheduleChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import noel.chronova.core.database.NotificationDao
import noel.chronova.core.database.NotificationEntity

/** The DAO in memory, doing what its queries say. */
private class MemoryNotificationDao : NotificationDao {
    val rows = MutableStateFlow<List<NotificationEntity>>(emptyList())
    private var next = 1L
    var prunes = 0

    override fun observe(limit: Int): Flow<List<NotificationEntity>> =
        rows.map { all -> all.sortedWith(compareByDescending<NotificationEntity> { it.createdAt }.thenByDescending { it.id }).take(limit) }

    override fun unreadCount(): Flow<Int> = rows.map { all -> all.count { !it.read } }
    override suspend fun insert(notification: NotificationEntity): Long = next++.also { rows.value = rows.value + notification.copy(id = it) }
    override suspend fun markRead(id: Long) = update(id) { it.copy(read = true) }
    override suspend fun markAllRead() {
        rows.value = rows.value.map { it.copy(read = true) }
    }
    override suspend fun clear() {
        rows.value = emptyList()
    }
    override suspend fun undo(id: Long): String? = rows.value.firstOrNull { it.id == id }?.undo
    override suspend fun forgetUndo(id: Long) = update(id) { it.copy(undo = null) }
    override suspend fun prune(since: Long, keep: Int) {
        prunes++
        val newest = rows.value.sortedByDescending { it.createdAt }.take(keep).map { it.id }.toSet()
        rows.value = rows.value.filter { it.createdAt >= since && it.id in newest }
    }

    private fun update(id: Long, change: (NotificationEntity) -> NotificationEntity) {
        rows.value = rows.value.map { if (it.id == id) change(it) else it }
    }
}

class RoomNotificationRepositoryTest {
    private val dao = MemoryNotificationDao()
    private val posted = mutableListOf<AppNotification>()
    private var now = 1_000_000_000L
    private val repository = RoomNotificationRepository(dao, poster = { posted += it }, clock = { now })

    private val before = SchedulerTask("t1", "p1", "Build", 1_000, 2_000, TaskColor.GREEN, 3)
    private val after = before.copy(startMillis = 5_000, endMillis = 6_000)
    private val link = TaskDependency("l1", "p1", "t0", "t1")

    @Test
    fun keepsWhatHappenedWithItsDetailsAndProject() = runTest {
        repository.add(NotificationKind.SYNCED, mapOf("sent" to "2", "received" to "1"))
        repository.add(NotificationKind.ASSISTANT_APPLIED, mapOf("count" to "1", "tasks" to "Build"), "p1", "Launch")
        val shown = repository.observe().first()
        assertEquals(listOf(NotificationKind.ASSISTANT_APPLIED, NotificationKind.SYNCED), shown.map { it.kind })
        assertEquals("Launch", shown.first().projectName)
        assertEquals(mapOf("sent" to "2", "received" to "1"), shown.last().args)
        assertEquals(2, repository.unreadCount().first())
        assertTrue(posted.isEmpty()) // neither is important
    }

    @Test
    fun importantOnesAlsoGoToTheShade() = runTest {
        repository.add(NotificationKind.SYNC_FAILED, mapOf("reason" to "SERVER"))
        assertEquals(listOf(NotificationKind.SYNC_FAILED), posted.map { it.kind })
        assertEquals(1L, posted.single().id)
    }

    @Test
    fun anAssistantChangeKeepsWhatUndoesItUntilUsed() = runTest {
        val undo = listOf(
            ScheduleChange.UpdateTask(after, before),
            ScheduleChange.CreateTask(before.copy(id = "t2")),
            ScheduleChange.DeleteTask(before.copy(id = "t3")),
            ScheduleChange.AddLink(link),
            ScheduleChange.RemoveLink(link.copy(id = "l2")),
        )
        repository.add(NotificationKind.ASSISTANT_APPLIED, projectId = "p1", undo = undo)
        val id = repository.observe().first().single().id
        assertTrue(repository.observe().first().single().canUndo)
        assertEquals(undo, repository.undo(id))

        repository.forgetUndo(id)
        assertNull(repository.undo(id))
        assertFalse(repository.observe().first().single().canUndo)
    }

    @Test
    fun undoKeepsProgressNotesAndMilestones() = runTest {
        val milestone = before.copy(id = "m1", endMillis = 1_000, progress = 100, isMilestone = true)
        val undo = listOf(
            ScheduleChange.UpdateTask(after.copy(progress = 100, notes = "Done\nfor now"), before.copy(progress = 40)),
            ScheduleChange.DeleteTask(milestone),
        )
        repository.add(NotificationKind.ASSISTANT_APPLIED, projectId = "p1", undo = undo)
        assertEquals(undo, repository.undo(repository.observe().first().single().id))
    }

    @Test
    fun anUndoStoredBeforeProgressReadsTheDefaults() = runTest {
        val oldJson = "[{\"type\":\"delete\",\"task\":{\"id\":\"t1\",\"projectId\":\"p1\",\"title\":\"Build\"," +
            "\"startMillis\":1000,\"endMillis\":2000,\"color\":\"GREEN\",\"sortOrder\":3}}]"
        dao.insert(NotificationEntity(kind = NotificationKind.ASSISTANT_APPLIED.name, args = "{}", projectId = "p1", projectName = null, createdAt = now, read = false, undo = oldJson))
        assertEquals(listOf(ScheduleChange.DeleteTask(before)), repository.undo(repository.observe().first().single().id))
    }

    @Test
    fun readingClearingAndPruning() = runTest {
        repository.add(NotificationKind.SIGNED_IN)
        now += 31L * 86_400_000L
        repository.add(NotificationKind.SYNCED) // the first is now over 30 days old
        assertEquals(listOf(NotificationKind.SYNCED), repository.observe().first().map { it.kind })
        repository.markAllRead()
        assertEquals(0, repository.unreadCount().first())
        repository.clear()
        assertTrue(repository.observe().first().isEmpty())
    }
}
