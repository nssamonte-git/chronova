package noel.chronova.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import noel.chronova.core.domain.sync.AccountSync
import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.domain.sync.SyncStatus

/** An [AccountSync] whose state tests set directly; records what was asked of it. */
open class FakeAccountSync : AccountSync {
    override val pendingCount = MutableStateFlow(0)
    override val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    var lastSynced: Long? = null
    var deleteFailure: SyncFailure? = null
    val calls = mutableListOf<String>()

    override fun lastSyncedAt(): Long? = lastSynced
    override fun syncNow() {
        calls += "syncNow"
    }
    override suspend fun signOutAndClear() {
        calls += "signOutAndClear"
    }
    override suspend fun deleteAccount(): SyncFailure? = deleteFailure.also { calls += "deleteAccount" }
}
