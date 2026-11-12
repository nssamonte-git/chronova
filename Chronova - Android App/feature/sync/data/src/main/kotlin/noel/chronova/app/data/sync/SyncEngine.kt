package noel.chronova.app.data.sync

import noel.chronova.app.data.remote.ChangeDto
import noel.chronova.app.data.remote.SyncApi
import noel.chronova.app.data.remote.SyncApiException
import noel.chronova.app.data.remote.SyncRequestDto

sealed interface SyncOutcome {
    /** [replaced]: changes sent from here that the server didn't take because its own copy was newer. */
    data class Synced(val pushed: Int, val pulled: Int, val replaced: Int = 0) : SyncOutcome
    data object NotSignedIn : SyncOutcome
    data class Failed(val kind: SyncApiException.Kind) : SyncOutcome
}

/**
 * One sync run: push every dirty record (in batches), pull everything since the
 * cursor (all pages), then apply the pulled changes and save the cursor.
 *
 * Pulled changes are applied together at the end so a task never arrives before the
 * project it belongs to. If anything fails, the cursor isn't saved and the next run
 * pulls the same changes again (applying them is idempotent).
 */
class SyncEngine(
    private val local: LocalSyncStore,
    private val api: SyncApi,
    private val accessToken: suspend () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(userId: String): SyncOutcome {
        val token = accessToken() ?: return SyncOutcome.NotSignedIn
        var cursor = local.cursor(userId)
        val pending = local.dirtyChanges()
        val pulled = mutableListOf<ChangeDto>()
        var stale = 0
        try {
            var hasMore = false
            // Always at least one request, so a device with nothing to push still pulls.
            for (batch in pending.chunked(MAX_CHANGES).ifEmpty { listOf(emptyList()) }) {
                val response = api.sync(token, SyncRequestDto(cursor, batch))
                local.markSynced(batch)
                // The server kept its newer copy of these (sent back with the pulled changes).
                stale += response.results.count { it.status == STALE }
                pulled += response.changes
                cursor = response.cursor
                hasMore = response.hasMore
            }
            while (hasMore) {
                val response = api.sync(token, SyncRequestDto(cursor, emptyList()))
                pulled += response.changes
                cursor = response.cursor
                hasMore = response.hasMore
            }
        } catch (error: SyncApiException) {
            return SyncOutcome.Failed(error.kind)
        }
        local.applyRemote(pulled)
        local.saveCursor(userId, checkNotNull(cursor), clock())
        return SyncOutcome.Synced(pushed = pending.size, pulled = pulled.size, replaced = stale)
    }

    companion object {
        /** The server accepts at most 500 changes per request. */
        const val MAX_CHANGES = 500

        /** A pushed change the server didn't take because its own copy is newer. */
        const val STALE = "stale"
    }
}
