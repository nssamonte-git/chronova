package noel.chronova.feature.sync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import noel.chronova.core.database.ChronovaDatabase
import noel.chronova.core.database.DependencyEntity
import noel.chronova.core.database.ProjectEntity
import noel.chronova.core.database.TaskEntity
import noel.chronova.feature.sync.data.remote.ChangeDto
import noel.chronova.feature.sync.data.remote.SyncEntities

/**
 * Room-backed [LocalSyncStore]. The cursor lives in (non-backed-up) preferences per
 * user, so a database restored from backup simply pulls everything again.
 */
class RoomLocalSyncStore(
    private val db: ChronovaDatabase,
    private val prefs: SharedPreferences,
) : LocalSyncStore {
    private val dao = db.syncDao()

    override suspend fun dirtyChanges(): List<ChangeDto> =
        dao.dirtyProjects().map { it.toChange() } +
            dao.dirtyTasks().map { it.toChange() } +
            dao.dirtyDependencies().map { it.toChange() }

    override suspend fun markSynced(pushed: List<ChangeDto>) {
        db.withTransaction {
            for (change in pushed) {
                when (change.entity) {
                    SyncEntities.PROJECT -> dao.markProjectClean(change.id, change.updatedAt)
                    SyncEntities.TASK -> dao.markTaskClean(change.id, change.updatedAt)
                    SyncEntities.DEPENDENCY -> dao.markDependencyClean(change.id, change.updatedAt)
                }
            }
        }
    }

    override suspend fun applyRemote(changes: List<ChangeDto>) {
        db.withTransaction {
            for (change in changes.sortedBy { SyncEntities.rank(it.entity) }) {
                // A malformed record from the server is skipped, not fatal.
                runCatching { apply(change) }
            }
        }
    }

    private suspend fun apply(change: ChangeDto) {
        when (change.entity) {
            SyncEntities.PROJECT -> {
                val local = dao.project(change.id)
                if (local.keepsLocal(change)) return
                val next = when {
                    change.deleted -> local?.copy(deleted = true, dirty = false, updatedAt = change.updatedAt) ?: return
                    else -> change.data!!.let { data ->
                        ProjectEntity(
                            id = change.id,
                            name = data.string("name"),
                            startDate = data.string("startDate"),
                            endDate = data.string("endDate"),
                            updatedAt = change.updatedAt,
                            deleted = false,
                            dirty = false,
                        )
                    }
                }
                dao.upsertProject(next)
            }
            SyncEntities.TASK -> {
                val local = dao.task(change.id)
                if (local.keepsLocal(change)) return
                val next = when {
                    change.deleted -> local?.copy(deleted = true, dirty = false, updatedAt = change.updatedAt) ?: return
                    else -> change.data!!.let { data ->
                        TaskEntity(
                            id = change.id,
                            projectId = data.string("projectId"),
                            title = data.string("title"),
                            startMillis = data.long("startMillis"),
                            endMillis = data.long("endMillis"),
                            color = data.string("color"),
                            sortOrder = data.int("sortOrder"),
                            updatedAt = change.updatedAt,
                            deleted = false,
                            dirty = false,
                        )
                    }
                }
                // The project row must exist (foreign key); it always does unless the data is inconsistent.
                if (dao.project(next.projectId) == null) return
                dao.upsertTask(next)
            }
            SyncEntities.DEPENDENCY -> {
                val local = dao.dependency(change.id)
                if (local.keepsLocal(change)) return
                val next = when {
                    change.deleted -> local?.copy(deleted = true, dirty = false, updatedAt = change.updatedAt) ?: return
                    else -> change.data!!.let { data ->
                        DependencyEntity(
                            id = change.id,
                            projectId = data.string("projectId"),
                            predecessorId = data.string("predecessorId"),
                            successorId = data.string("successorId"),
                            updatedAt = change.updatedAt,
                            deleted = false,
                            dirty = false,
                        )
                    }
                }
                if (dao.project(next.projectId) == null) return
                dao.upsertDependency(next)
            }
        }
    }

    override fun pendingCount(): Flow<Int> = dao.observePendingCount()

    override fun cursor(userId: String): String? = prefs.getString(cursorKey(userId), null)

    override fun lastSyncedAt(userId: String): Long? =
        prefs.getLong(syncedAtKey(userId), 0L).takeIf { it > 0L }

    override fun saveCursor(userId: String, cursor: String, syncedAt: Long) {
        prefs.edit {
            putString(cursorKey(userId), cursor)
            putLong(syncedAtKey(userId), syncedAt)
        }
    }

    override suspend fun clearAll() {
        // clearAllTables() blocks and Room forbids it on the main thread; callers may be on Main.
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            prefs.edit { clear() }
        }
    }

    override fun owner(): String? = prefs.getString(KEY_OWNER, null)

    override fun setOwner(userId: String) = prefs.edit { putString(KEY_OWNER, userId) }

    private fun cursorKey(userId: String) = "cursor_$userId"
    private fun syncedAtKey(userId: String) = "synced_at_$userId"

    private companion object {
        const val KEY_OWNER = "owner"
    }
}

/** A local edit newer than the incoming version (still unsynced) wins; ties go to the server. */
private fun ProjectEntity?.keepsLocal(change: ChangeDto) = this != null && dirty && updatedAt > change.updatedAt
private fun TaskEntity?.keepsLocal(change: ChangeDto) = this != null && dirty && updatedAt > change.updatedAt
private fun DependencyEntity?.keepsLocal(change: ChangeDto) = this != null && dirty && updatedAt > change.updatedAt

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long
private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

internal fun ProjectEntity.toChange() = ChangeDto(
    entity = SyncEntities.PROJECT,
    id = id,
    deleted = deleted,
    updatedAt = updatedAt,
    data = if (deleted) null else JsonObject(
        mapOf("name" to JsonPrimitive(name), "startDate" to JsonPrimitive(startDate), "endDate" to JsonPrimitive(endDate)),
    ),
)

internal fun TaskEntity.toChange() = ChangeDto(
    entity = SyncEntities.TASK,
    id = id,
    deleted = deleted,
    updatedAt = updatedAt,
    data = if (deleted) null else JsonObject(
        mapOf(
            "projectId" to JsonPrimitive(projectId),
            "title" to JsonPrimitive(title),
            "startMillis" to JsonPrimitive(startMillis),
            "endMillis" to JsonPrimitive(endMillis),
            "color" to JsonPrimitive(color),
            "sortOrder" to JsonPrimitive(sortOrder),
        ),
    ),
)

internal fun DependencyEntity.toChange() = ChangeDto(
    entity = SyncEntities.DEPENDENCY,
    id = id,
    deleted = deleted,
    updatedAt = updatedAt,
    data = if (deleted) null else JsonObject(
        mapOf(
            "projectId" to JsonPrimitive(projectId),
            "predecessorId" to JsonPrimitive(predecessorId),
            "successorId" to JsonPrimitive(successorId),
        ),
    ),
)
