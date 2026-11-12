package noel.chronova.core.ui.sync

import noel.chronova.core.domain.sync.SyncStatus

/** The domain's sync status, as the screens show it (when and how much it synced isn't shown here). */
fun SyncStatus.toSyncPhase(): SyncPhase = when (this) {
    SyncStatus.Idle -> SyncPhase.Idle
    SyncStatus.Syncing -> SyncPhase.Syncing
    is SyncStatus.Synced -> SyncPhase.Synced
    is SyncStatus.Failed -> SyncPhase.Failed(reason)
}
