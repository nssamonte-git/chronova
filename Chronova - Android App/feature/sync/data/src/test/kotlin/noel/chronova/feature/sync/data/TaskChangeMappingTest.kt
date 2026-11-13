package noel.chronova.feature.sync.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import noel.chronova.core.database.TaskEntity
import noel.chronova.feature.sync.data.remote.ChangeDto
import noel.chronova.feature.sync.data.remote.SyncEntities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The task wire mapping both ways (docs/sync-protocol.md, task fields). */
class TaskChangeMappingTest {
    private val stored = TaskEntity(
        id = "t1", projectId = "p1", title = "Build", startMillis = 1_000, endMillis = 2_000, color = "GREEN",
        sortOrder = 3, updatedAt = 50, deleted = false, dirty = false, progress = 40, notes = "Line 1\nLine 2",
    )

    @Test
    fun aTaskSurvivesTheRoundTrip() {
        assertEquals(stored, stored.toChange().toTaskEntity())
    }

    @Test
    fun aMilestoneSurvivesTheRoundTrip() {
        val milestone = stored.copy(endMillis = 1_000, progress = 100, isMilestone = true)
        assertEquals(milestone, milestone.toChange().toTaskEntity())
    }

    @Test
    fun pushesEveryField() {
        val data = stored.toChange().data!!
        assertEquals(
            setOf("projectId", "title", "startMillis", "endMillis", "color", "sortOrder", "progress", "notes", "milestone"),
            data.keys,
        )
    }

    @Test
    fun aTombstoneCarriesNoData() {
        assertNull(stored.copy(deleted = true).toChange().data)
    }

    @Test
    fun fieldsAddedLaterDefaultWhenAbsent() {
        val old = change(withoutKeys = setOf("progress", "notes", "milestone"))
        assertEquals(stored.copy(progress = 0, notes = ""), old.toTaskEntity())
    }

    @Test
    fun tasksThatBreakTheRulesAreSkipped() {
        assertNull(change(mapOf("progress" to JsonPrimitive(101))).toTaskEntity())
        assertNull(change(mapOf("endMillis" to JsonPrimitive(1_000))).toTaskEntity()) // zero length, not a milestone
        assertNull(change(mapOf("milestone" to JsonPrimitive(true))).toTaskEntity()) // a milestone with a length
        assertNull(
            change(mapOf("milestone" to JsonPrimitive(true), "endMillis" to JsonPrimitive(1_000), "progress" to JsonPrimitive(50)))
                .toTaskEntity(),
        )
    }

    private fun change(overrides: Map<String, JsonPrimitive> = emptyMap(), withoutKeys: Set<String> = emptySet()): ChangeDto {
        val data = stored.toChange().data!!
        return ChangeDto(
            entity = SyncEntities.TASK,
            id = "t1",
            deleted = false,
            updatedAt = 50,
            data = JsonObject(data.filterKeys { it !in withoutKeys } + overrides),
        )
    }
}
