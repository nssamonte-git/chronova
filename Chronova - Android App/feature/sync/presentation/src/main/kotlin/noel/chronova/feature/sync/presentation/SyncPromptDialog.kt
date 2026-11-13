package noel.chronova.feature.sync.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import noel.chronova.core.ui.R as CoreUiR

/** "You're back online" or "Changes on another device": asks before syncing, as the user requires. */
@Composable
fun SyncPromptDialog(prompt: SyncPromptUi, onSync: () -> Unit, onSignIn: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(if (prompt.fromOtherDevice) R.string.sync_remote_title else R.string.sync_prompt_title)) },
        text = {
            Text(
                if (prompt.fromOtherDevice) {
                    stringResource(R.string.sync_remote_body)
                } else {
                    pluralStringResource(
                        if (prompt.signedIn) R.plurals.sync_prompt_body else R.plurals.sync_prompt_body_signed_out,
                        prompt.pendingChanges,
                        prompt.pendingChanges,
                    )
                },
            )
        },
        confirmButton = {
            if (prompt.signedIn) {
                TextButton(onClick = onSync) { Text(stringResource(CoreUiR.string.sync_now)) }
            } else {
                TextButton(onClick = onSignIn) { Text(stringResource(R.string.sync_sign_in)) }
            }
        },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.sync_later)) } },
    )
}
