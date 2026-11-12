package noel.chronova.feature.sync.presentation

import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.domain.sync.SyncPrompt
import noel.chronova.core.domain.sync.SyncStatus
import noel.chronova.core.ui.sync.SyncPhase
import noel.chronova.core.ui.sync.SyncUi
import noel.chronova.feature.sync.domain.SyncOverview
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncUiMappersTest {
    @Test
    fun overviewBecomesTheSyncLine() {
        val overview = SyncOverview(configured = true, signedIn = true, pending = 3, status = SyncStatus.Failed(SyncFailure.OFFLINE))
        assertEquals(SyncUi(configured = true, signedIn = true, pending = 3, phase = SyncPhase.Failed(SyncFailure.OFFLINE)), overview.toUi())
    }

    @Test
    fun promptKeepsItsCountAndSignIn() {
        assertEquals(SyncPromptUi(pendingChanges = 2, signedIn = false), SyncPrompt(pendingChanges = 2, signedIn = false).toUi())
    }
}
