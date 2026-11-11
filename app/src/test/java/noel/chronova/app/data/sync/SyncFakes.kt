package noel.chronova.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import noel.chronova.app.data.remote.ChangeDto
import noel.chronova.app.data.remote.SyncApi
import noel.chronova.app.data.remote.SyncApiException
import noel.chronova.app.data.remote.SyncRequestDto
import noel.chronova.app.data.remote.SyncResponseDto
import noel.chronova.app.data.remote.SyncResultDto

/**
 * In-memory server with the protocol's semantics (backend README / server docs):
 * last write wins by updatedAt (ties keep the server copy), per-user seq cursor,
 * paging, and stale pushes answered with the winning copy.
 */
class FakeServer(private val pageSize: Int = 500) : SyncApi {
    private data class Stored(val change: ChangeDto, val seq: Long)

    private val records = linkedMapOf<String, Stored>()
    private var seq = 0L
    val requests = mutableListOf<SyncRequestDto>()
    var failNext: SyncApiException.Kind? = null
    var deletedAccount = false

    fun stored(entity: String, id: String): ChangeDto? = records["$entity#$id"]?.change

    /** Another device writes directly. */
    fun put(change: ChangeDto) {
        records["${change.entity}#${change.id}"] = Stored(change, ++seq)
    }

    override suspend fun sync(accessToken: String, request: SyncRequestDto): SyncResponseDto {
        requests += request
        failNext?.let {
            failNext = null
            throw SyncApiException(it)
        }
        val winners = mutableListOf<ChangeDto>()
        val results = request.changes.map { change ->
            val key = "${change.entity}#${change.id}"
            val existing = records[key]
            if (existing == null || existing.change.updatedAt < change.updatedAt) {
                records[key] = Stored(change, ++seq)
                SyncResultDto(change.entity, change.id, "applied")
            } else {
                winners += existing.change
                SyncResultDto(change.entity, change.id, "stale")
            }
        }
        val cursor = request.cursor?.toLong() ?: 0L
        val after = records.values.filter { it.seq > cursor }.sortedBy { it.seq }
        val page = after.take(pageSize)
        val inPage = page.map { "${it.change.entity}#${it.change.id}" }.toSet()
        return SyncResponseDto(
            results = results,
            changes = page.map { it.change } + winners.filter { "${it.entity}#${it.id}" !in inPage },
            cursor = (page.lastOrNull()?.seq ?: cursor).toString(),
            hasMore = after.size > pageSize,
        )
    }

    override suspend fun deleteAccount(accessToken: String) {
        failNext?.let {
            failNext = null
            throw SyncApiException(it)
        }
        deletedAccount = true
    }
}

/** In-memory device store with the same LWW rule as RoomLocalSyncStore. */
class FakeLocalStore : LocalSyncStore {
    data class Row(val change: ChangeDto, val dirty: Boolean)

    val rows = MutableStateFlow<Map<String, Row>>(emptyMap())
    private val cursors = mutableMapOf<String, Pair<String, Long>>()
    var cleared = false

    fun edit(change: ChangeDto) {
        rows.value = rows.value + ("${change.entity}#${change.id}" to Row(change, dirty = true))
    }

    fun row(entity: String, id: String) = rows.value["$entity#$id"]

    override suspend fun dirtyChanges() = rows.value.values.filter { it.dirty }.map { it.change }

    override suspend fun markSynced(pushed: List<ChangeDto>) {
        rows.value = rows.value.mapValues { (_, row) ->
            if (pushed.any { it.entity == row.change.entity && it.id == row.change.id && it.updatedAt == row.change.updatedAt }) {
                row.copy(dirty = false)
            } else {
                row
            }
        }
    }

    override suspend fun applyRemote(changes: List<ChangeDto>) {
        for (change in changes) {
            val key = "${change.entity}#${change.id}"
            val local = rows.value[key]
            if (local != null && local.dirty && local.change.updatedAt > change.updatedAt) continue
            rows.value = rows.value + (key to Row(change, dirty = false))
        }
    }

    override fun pendingCount() = rows.map { all -> all.values.count { it.dirty } }
    override fun cursor(userId: String) = cursors[userId]?.first
    override fun lastSyncedAt(userId: String) = cursors[userId]?.second
    override fun saveCursor(userId: String, cursor: String, syncedAt: Long) {
        cursors[userId] = cursor to syncedAt
    }

    override suspend fun clearAll() {
        rows.value = emptyMap()
        cursors.clear()
        cleared = true
    }
}

fun task(id: String, updatedAt: Long, title: String = "Task", deleted: Boolean = false) = ChangeDto(
    entity = "task",
    id = id,
    deleted = deleted,
    updatedAt = updatedAt,
    data = if (deleted) null else kotlinx.serialization.json.JsonObject(
        mapOf("title" to kotlinx.serialization.json.JsonPrimitive(title)),
    ),
)
