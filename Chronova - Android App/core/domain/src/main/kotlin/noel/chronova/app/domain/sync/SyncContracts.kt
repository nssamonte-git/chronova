package noel.chronova.app.domain.sync

import kotlinx.coroutines.flow.StateFlow

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long, val pushed: Int, val pulled: Int) : SyncStatus
    data class Failed(val reason: SyncFailure) : SyncStatus
}

enum class SyncFailure { NOT_SIGNED_IN, SIGN_IN_AGAIN, OFFLINE, SERVER, BUSY }

/** "You're back online with unsynced changes — sync now?" */
data class SyncPrompt(val pendingChanges: Int, val signedIn: Boolean)

/** What the account screen needs from sync; implemented by the sync data layer, faked in tests. */
interface AccountSync {
    val pendingCount: StateFlow<Int>
    val status: StateFlow<SyncStatus>
    fun lastSyncedAt(): Long?
    fun syncNow()
    suspend fun signOutAndClear()
    suspend fun deleteAccount(): SyncFailure?
}
