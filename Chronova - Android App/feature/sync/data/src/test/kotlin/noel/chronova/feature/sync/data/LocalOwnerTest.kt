package noel.chronova.feature.sync.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalOwnerTest {
    private val local = FakeLocalStore()

    @Test
    fun dataMadeBeforeSigningInBecomesTheirs() = runTest {
        local.edit(task("t1", 1))
        local.keepOnlyFor("ana")
        assertFalse(local.cleared)
        assertEquals("ana", local.owner())
    }

    @Test
    fun theSameAccountKeepsItsData() = runTest {
        local.setOwner("ana")
        local.edit(task("t1", 1))
        local.keepOnlyFor("ana")
        assertFalse(local.cleared)
        assertEquals(1, local.dirtyChanges().size)
    }

    @Test
    fun anotherAccountNeverSeesIt() = runTest {
        local.setOwner("ana")
        local.edit(task("t1", 1))
        local.keepOnlyFor("ben")
        assertTrue(local.cleared)
        assertTrue(local.dirtyChanges().isEmpty())
        assertEquals("ben", local.owner())
    }
}
