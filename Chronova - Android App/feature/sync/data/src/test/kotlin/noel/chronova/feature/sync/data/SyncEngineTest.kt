package noel.chronova.feature.sync.data

import kotlinx.coroutines.test.runTest
import noel.chronova.feature.sync.data.remote.SyncApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {
    private val server = FakeServer(pageSize = 3)
    private val local = FakeLocalStore()
    private var token: String? = "token"
    private val engine = SyncEngine(local, server, accessToken = { token }, clock = { 42L })

    @Test
    fun pushesDirtyRecordsAndMarksThemClean() = runTest {
        local.edit(task("t1", 10))
        local.edit(task("t2", 11))
        val outcome = engine.sync("alice")
        assertEquals(SyncOutcome.Synced(pushed = 2, pulled = 2), outcome) // echoes come back
        assertEquals(10L, server.stored("task", "t1")?.updatedAt)
        assertFalse(local.row("task", "t1")!!.dirty)
        assertEquals("2", local.cursor("alice"))
        assertEquals(42L, local.lastSyncedAt("alice"))
    }

    @Test
    fun pullsOtherDevicesChangesAcrossPages() = runTest {
        (1..7).forEach { server.put(task("t$it", it.toLong())) }
        val outcome = engine.sync("alice") as SyncOutcome.Synced
        assertEquals(7, outcome.pulled)
        assertEquals(3, server.requests.size) // pages of 3: 3 + 3 + 1
        assertEquals((1..7).map { "t$it" }.toSet(), local.rows.value.values.map { it.change.id }.toSet())
        assertEquals("7", local.cursor("alice"))
    }

    @Test
    fun aNewerServerEditWinsAndReplacesTheLocalOne() = runTest {
        server.put(task("t1", 100, "Server"))
        local.edit(task("t1", 50, "Older local edit"))
        val outcome = engine.sync("alice") as SyncOutcome.Synced
        assertEquals(1, outcome.replaced) // the notifications page says the newer copy was kept
        assertEquals("Server", local.row("task", "t1")!!.change.data.toString().substringAfter(":").trim('"', '}'))
        assertFalse(local.row("task", "t1")!!.dirty)
    }

    @Test
    fun staleWinnerArrivesEvenWhenBehindTheCursor() = runTest {
        server.put(task("t1", 100, "Server"))
        engine.sync("alice") // cursor now past the server copy
        local.edit(task("t1", 60, "Slow clock"))
        engine.sync("alice")
        assertEquals(100L, local.row("task", "t1")!!.change.updatedAt)
        assertFalse(local.row("task", "t1")!!.dirty)
    }

    @Test
    fun failuresKeepChangesDirtyAndTheCursorUnchanged() = runTest {
        local.edit(task("t1", 10))
        server.failNext = SyncApiException.Kind.NETWORK
        assertEquals(SyncOutcome.Failed(SyncApiException.Kind.NETWORK), engine.sync("alice"))
        assertTrue(local.row("task", "t1")!!.dirty)
        assertNull(local.cursor("alice"))
    }

    @Test
    fun noTokenMeansNotSignedIn() = runTest {
        token = null
        assertEquals(SyncOutcome.NotSignedIn, engine.sync("alice"))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun largePushesGoInBatchesOfFiveHundred() = runTest {
        val big = FakeServer(pageSize = 2000)
        val engine = SyncEngine(local, big, accessToken = { "t" })
        (1..1201).forEach { local.edit(task("t$it", it.toLong())) }
        engine.sync("alice")
        assertEquals(listOf(500, 500, 201), big.requests.map { it.changes.size })
        assertEquals(0, local.rows.value.values.count { it.dirty })
    }
}
