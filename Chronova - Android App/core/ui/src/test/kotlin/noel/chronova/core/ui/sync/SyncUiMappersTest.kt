package noel.chronova.core.ui.sync

import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.domain.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncUiMappersTest {
    @Test
    fun everyStatusHasItsPhase() {
        assertEquals(SyncPhase.Idle, SyncStatus.Idle.toSyncPhase())
        assertEquals(SyncPhase.Syncing, SyncStatus.Syncing.toSyncPhase())
        assertEquals(SyncPhase.Synced, SyncStatus.Synced(at = 5L, pushed = 2, pulled = 3).toSyncPhase())
        SyncFailure.entries.forEach { assertEquals(SyncPhase.Failed(it), SyncStatus.Failed(it).toSyncPhase()) }
    }
}
