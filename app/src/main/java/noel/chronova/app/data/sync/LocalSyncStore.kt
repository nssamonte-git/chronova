package noel.chronova.app.data.sync

import kotlinx.coroutines.flow.Flow
import noel.chronova.app.data.remote.ChangeDto

/** The device's side of sync. Implemented over Room; faked in tests. */
interface LocalSyncStore {
    /** Every locally changed record (tombstones included), as it is right now. */
    suspend fun dirtyChanges(): List<ChangeDto>

    /** The server has these exact versions: clear `dirty` unless edited again since. */
    suspend fun markSynced(pushed: List<ChangeDto>)

    /**
     * Applies server changes, last write wins: a record edited locally after the
     * incoming version is kept (it is pushed next time). Parents go first.
     */
    suspend fun applyRemote(changes: List<ChangeDto>)

    fun pendingCount(): Flow<Int>

    fun cursor(userId: String): String?
    fun lastSyncedAt(userId: String): Long?
    fun saveCursor(userId: String, cursor: String, syncedAt: Long)

    /** Removes all projects, tasks, links, view settings and sync state (sign-out). */
    suspend fun clearAll()
}
