package noel.chronova.feature.sync.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import noel.chronova.core.ui.R as CoreUiR
import noel.chronova.core.ui.components.ConfirmDialog

/** "You're back online" or "Changes on another device": asks before syncing, as the user requires. */
@Composable
fun SyncPromptDialog(prompt: SyncPromptUi, onSync: () -> Unit, onSignIn: () -> Unit, onLater: () -> Unit) {
    ConfirmDialog(
        title = stringResource(if (prompt.fromOtherDevice) R.string.sync_remote_title else R.string.sync_prompt_title),
        text = if (prompt.fromOtherDevice) {
            stringResource(R.string.sync_remote_body)
        } else {
            pluralStringResource(
                if (prompt.signedIn) R.plurals.sync_prompt_body else R.plurals.sync_prompt_body_signed_out,
                prompt.pendingChanges,
                prompt.pendingChanges,
            )
        },
        confirmLabel = stringResource(if (prompt.signedIn) CoreUiR.string.sync_now else R.string.sync_sign_in),
        onConfirm = if (prompt.signedIn) onSync else onSignIn,
        onDismiss = onLater,
        dismissLabel = stringResource(R.string.sync_later),
    )
}
