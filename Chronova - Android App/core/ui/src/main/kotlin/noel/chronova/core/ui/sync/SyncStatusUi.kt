package noel.chronova.core.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import noel.chronova.core.ui.R
import noel.chronova.core.domain.sync.SyncFailure

/** What sync is doing, as the screens show it. */
sealed interface SyncPhase {
    data object Idle : SyncPhase
    data object Syncing : SyncPhase
    data object Synced : SyncPhase
    data class Failed(val reason: SyncFailure) : SyncPhase
}

/** What the project list shows about sync. */
data class SyncUi(
    /** False in builds without a backend: no sync UI at all. */
    val configured: Boolean = false,
    val signedIn: Boolean = false,
    val pending: Int = 0,
    val phase: SyncPhase = SyncPhase.Idle,
)

/** Top-bar sync button with the number of unsynced changes. */
@Composable
fun SyncButton(ui: SyncUi, onSync: () -> Unit) {
    IconButton(onClick = onSync, enabled = ui.phase != SyncPhase.Syncing) {
        if (ui.phase == SyncPhase.Syncing) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            BadgedBox(badge = { if (ui.pending > 0) Badge { Text(if (ui.pending > 99) "99+" else "${ui.pending}") } }) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sync_now))
            }
        }
    }
}

/** One line under the top bar: what sync is doing or why it failed. Tapping it opens the account screen. */
@Composable
fun SyncStatusLine(ui: SyncUi, onClick: () -> Unit) {
    val text = syncStatusText(ui) ?: return
    val isError = ui.phase is SyncPhase.Failed
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun syncStatusText(ui: SyncUi): String? {
    if (!ui.configured) return null
    return when (val phase = ui.phase) {
        SyncPhase.Syncing -> stringResource(R.string.sync_status_syncing)
        is SyncPhase.Failed -> syncFailureText(phase.reason)
        SyncPhase.Synced ->
            if (ui.pending > 0) pluralStringResource(R.plurals.sync_status_pending, ui.pending, ui.pending)
            else stringResource(R.string.sync_status_synced)
        SyncPhase.Idle -> when {
            ui.pending == 0 -> null
            ui.signedIn -> pluralStringResource(R.plurals.sync_status_pending, ui.pending, ui.pending)
            else -> pluralStringResource(R.plurals.sync_status_signed_out, ui.pending, ui.pending)
        }
    }
}

@Composable
fun syncFailureText(reason: SyncFailure): String = stringResource(
    when (reason) {
        SyncFailure.NOT_SIGNED_IN -> R.string.sync_failed_not_signed_in
        SyncFailure.SIGN_IN_AGAIN -> R.string.sync_failed_sign_in_again
        SyncFailure.OFFLINE -> R.string.sync_failed_offline
        SyncFailure.BUSY -> R.string.sync_failed_busy
        SyncFailure.SERVER -> R.string.sync_failed_server
    },
)
