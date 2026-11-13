package noel.chronova.feature.sync.presentation

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme

/** "You're back online", signed in: offers to sync. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun SyncPromptSignedIn() = ChronovaTheme { SyncPromptDialog(SyncPromptUi(pendingChanges = 3, signedIn = true), onSync = {}, onSignIn = {}, onLater = {}) }

/** "You're back online", signed out: asks to sign in. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun SyncPromptSignedOut() = ChronovaTheme { SyncPromptDialog(SyncPromptUi(pendingChanges = 1, signedIn = false), onSync = {}, onSignIn = {}, onLater = {}) }

/** A tapped "changes on another device" alert: offers to sync them here. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun SyncPromptFromOtherDevice() = ChronovaTheme {
    SyncPromptDialog(SyncPromptUi(pendingChanges = 0, signedIn = true, fromOtherDevice = true), onSync = {}, onSignIn = {}, onLater = {})
}
