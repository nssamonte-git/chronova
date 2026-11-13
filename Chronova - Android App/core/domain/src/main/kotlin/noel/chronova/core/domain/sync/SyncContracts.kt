package noel.chronova.core.domain.sync

import kotlinx.coroutines.flow.StateFlow

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long, val pushed: Int, val pulled: Int) : SyncStatus
    data class Failed(val reason: SyncFailure) : SyncStatus
}

enum class SyncFailure { NOT_SIGNED_IN, SIGN_IN_AGAIN, OFFLINE, SERVER, BUSY }

/**
 * "You're back online with unsynced changes — sync now?", or with [fromOtherDevice]
 * "your projects changed on another device — sync now?" (a tapped push).
 */
data class SyncPrompt(val pendingChanges: Int, val signedIn: Boolean, val fromOtherDevice: Boolean = false)

/** Read-only view of sync for screens: unsynced changes, the last result, the last sync time. */
interface SyncStatusQueries {
    val pendingCount: StateFlow<Int>
    val status: StateFlow<SyncStatus>
    fun lastSyncedAt(): Long?
}

/** What the account screen needs from sync; implemented by the sync data layer, faked in tests. */
interface AccountSync : SyncStatusQueries {
    fun syncNow()
    suspend fun signOutAndClear()
    suspend fun deleteAccount(): SyncFailure?
}
