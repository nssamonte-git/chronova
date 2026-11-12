package noel.chronova.core.domain

import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskDependency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyRulesTest {

    private fun link(from: String, to: String) = TaskDependency("$from>$to", "p", from, to)

    // a -> b -> c, and d on its own.
    private val chain = listOf(link("a", "b"), link("b", "c"))

    @Test
    fun forbiddenPredecessorsAreTheTaskAndEverythingDownstream() {
        assertEquals(setOf("a", "b", "c"), DependencyRules.forbiddenPredecessors("a", chain))
        assertEquals(setOf("c"), DependencyRules.forbiddenPredecessors("c", chain))
        assertEquals(setOf("d"), DependencyRules.forbiddenPredecessors("d", chain))
    }

    @Test
    fun canLinkRejectsCyclesAndDuplicates() {
        assertFalse(DependencyRules.canLink("c", "a", chain)) // would close a -> b -> c -> a
        assertFalse(DependencyRules.canLink("a", "b", chain)) // already linked
        assertTrue(DependencyRules.canLink("a", "c", chain)) // a shortcut is fine
        assertTrue(DependencyRules.canLink("d", "a", chain))
    }

    @Test
    fun branchingGraphsAreWalkedFully() {
        val graph = listOf(link("a", "b"), link("a", "c"), link("c", "d"), link("b", "d"))
        assertEquals(setOf("a", "b", "c", "d"), DependencyRules.forbiddenPredecessors("a", graph))
    }

    @Test
    fun violationIsSuccessorStartingBeforePredecessorEnds() {
        val pred = SchedulerTask("a", "p", "A", 0L, 100L)
        val link = link("a", "b")
        assertFalse(link.isViolated(pred, SchedulerTask("b", "p", "B", 100L, 200L)))
        assertTrue(link.isViolated(pred, SchedulerTask("b", "p", "B", 99L, 200L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun selfLinksAreRejected() {
        link("a", "a")
    }
}
