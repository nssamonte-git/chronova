package noel.chronova.core.ui.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.domain.sync.SyncFailure

/** The sync status line in each state it can show. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun SyncStatusLines() = ChronovaTheme {
    Surface {
        Column {
            listOf(
                SyncUi(configured = true, signedIn = true, phase = SyncPhase.Syncing),
                SyncUi(configured = true, signedIn = true, phase = SyncPhase.Synced),
                SyncUi(configured = true, signedIn = true, phase = SyncPhase.Failed(SyncFailure.OFFLINE)),
                SyncUi(configured = true, signedIn = true, pending = 4, phase = SyncPhase.Synced),
                SyncUi(configured = true, signedIn = false, pending = 3),
            ).forEach {
                SyncStatusLine(it, onClick = {})
                HorizontalDivider()
            }
        }
    }
}

/** The sync button: a badge with the count, capped at "99+", and the spinner while syncing. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun SyncButtons() = ChronovaTheme {
    Surface {
        // Spaced as in a top bar, so a badge isn't cut by the image edge.
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SyncButton(SyncUi(configured = true, signedIn = true, pending = 5), onSync = {})
            SyncButton(SyncUi(configured = true, signedIn = true, pending = 150), onSync = {})
            SyncButton(SyncUi(configured = true, signedIn = true, phase = SyncPhase.Syncing), onSync = {})
        }
    }
}
