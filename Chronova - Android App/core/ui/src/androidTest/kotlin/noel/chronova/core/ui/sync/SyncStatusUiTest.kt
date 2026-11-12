package noel.chronova.core.ui.sync

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.domain.sync.SyncStatus
import noel.chronova.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The sync status line and button, rendered on a device (this module's own test app). */
@RunWith(AndroidJUnit4::class)
class SyncStatusUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val synced = SyncStatus.Synced(at = 1L, pushed = 0, pulled = 0)
    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** What [syncStatusText] says for each of [uis] (one composition: a test sets content once). */
    private fun textsFor(vararg uis: SyncUi): List<String?> {
        val texts = arrayOfNulls<String>(uis.size)
        compose.setContent { uis.forEachIndexed { i, ui -> texts[i] = syncStatusText(ui) } }
        compose.waitForIdle()
        return texts.toList()
    }

    @Test
    fun saysNothingWithoutABackendOrWithNothingToSay() {
        val texts = textsFor(
            SyncUi(configured = false, pending = 3, status = SyncStatus.Failed(SyncFailure.OFFLINE)),
            SyncUi(configured = false, status = SyncStatus.Syncing),
            SyncUi(configured = true, signedIn = true, pending = 0, status = SyncStatus.Idle),
        )
        texts.forEach { assertNull(it) }
    }

    @Test
    fun saysWhatSyncIsDoing() {
        val texts = textsFor(
            SyncUi(configured = true, signedIn = true, status = SyncStatus.Syncing),
            SyncUi(configured = true, signedIn = true, status = synced),
            SyncUi(configured = true, signedIn = true, pending = 2, status = synced),
        )
        assertEquals(
            listOf(
                res.getString(R.string.sync_status_syncing),
                res.getString(R.string.sync_status_synced),
                res.getQuantityString(R.plurals.sync_status_pending, 2, 2),
            ),
            texts,
        )
    }

    @Test
    fun eachFailureHasItsOwnWording() {
        val expected = mapOf(
            SyncFailure.NOT_SIGNED_IN to R.string.sync_failed_not_signed_in,
            SyncFailure.SIGN_IN_AGAIN to R.string.sync_failed_sign_in_again,
            SyncFailure.OFFLINE to R.string.sync_failed_offline,
            SyncFailure.BUSY to R.string.sync_failed_busy,
            SyncFailure.SERVER to R.string.sync_failed_server,
        )
        val failures = SyncFailure.entries
        val texts = textsFor(*failures.map { SyncUi(configured = true, signedIn = true, status = SyncStatus.Failed(it)) }.toTypedArray())
        assertEquals(failures.map { res.getString(expected.getValue(it)) }, texts)
        assertEquals(failures.size, texts.toSet().size)
    }

    @Test
    fun pendingChangesUseThePluralAndSayWhenSignedOut() {
        val texts = textsFor(
            SyncUi(configured = true, signedIn = true, pending = 1),
            SyncUi(configured = true, signedIn = true, pending = 4),
            SyncUi(configured = true, signedIn = false, pending = 1),
            SyncUi(configured = true, signedIn = false, pending = 4),
        )
        assertEquals(
            listOf(
                res.getQuantityString(R.plurals.sync_status_pending, 1, 1),
                res.getQuantityString(R.plurals.sync_status_pending, 4, 4),
                res.getQuantityString(R.plurals.sync_status_signed_out, 1, 1),
                res.getQuantityString(R.plurals.sync_status_signed_out, 4, 4),
            ),
            texts,
        )
        // The singular and plural differ (the right quantity is picked, not just the number swapped).
        assertEquals(false, texts[0]!!.replace("1", "") == texts[1]!!.replace("4", ""))
    }

    @Test
    fun statusLineShowsTheTextAndOpensOnTap() {
        var taps = 0
        compose.setContent {
            SyncStatusLine(SyncUi(configured = true, signedIn = true, status = SyncStatus.Failed(SyncFailure.OFFLINE))) { taps++ }
        }
        compose.onNodeWithText(res.getString(R.string.sync_failed_offline)).assertIsDisplayed().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun buttonSyncsAndShowsThePendingCount() {
        var syncs = 0
        compose.setContent { SyncButton(SyncUi(configured = true, signedIn = true, pending = 5)) { syncs++ } }
        compose.onNodeWithText("5").assertExists()
        compose.onNodeWithContentDescription(res.getString(R.string.sync_now)).assertIsEnabled().performClick()
        assertEquals(1, syncs)
    }

    @Test
    fun buttonBadgeCapsAtNinetyNinePlus() {
        compose.setContent { SyncButton(SyncUi(configured = true, signedIn = true, pending = 150)) {} }
        compose.onNodeWithText("99+").assertExists()
        compose.onNodeWithText("150").assertDoesNotExist()
    }

    @Test
    fun buttonHasNoBadgeWhenNothingIsPending() {
        compose.setContent { SyncButton(SyncUi(configured = true, signedIn = true, pending = 0)) {} }
        compose.onNodeWithText("0").assertDoesNotExist()
        compose.onNodeWithContentDescription(res.getString(R.string.sync_now)).assertExists()
    }

    @Test
    fun buttonIsDisabledWhileSyncing() {
        var syncs = 0
        compose.setContent { SyncButton(SyncUi(configured = true, signedIn = true, pending = 3, status = SyncStatus.Syncing)) { syncs++ } }
        compose.onNode(button).assertIsNotEnabled().performClick()
        // A spinner instead of the icon and badge.
        compose.onNodeWithContentDescription(res.getString(R.string.sync_now)).assertDoesNotExist()
        compose.onNodeWithText("3").assertDoesNotExist()
        assertEquals(0, syncs)
    }
}
