package noel.chronova.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme

/** The top bar, a card, the three banners, the progress meter and the floating button. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ComponentsOnAPage() = ChronovaTheme {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChronovaTopBar(
            title = { ChronovaTopBarTitle("Website launch") },
            onBack = {},
            actions = {
                IconButton(onClick = {}) { Icon(Icons.Filled.Search, contentDescription = null) }
                IconButton(onClick = {}) { Icon(Icons.Filled.Settings, contentDescription = null) }
            },
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChronovaCard(Modifier.fillMaxWidth(), onClick = {}) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Office move", style = MaterialTheme.typography.titleMedium)
                    Text("2 Nov – 20 Nov 2026", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ProgressMeter(0.4f, Modifier.fillMaxWidth().height(6.dp))
                }
            }
            SectionHeader("Messages")
            InlineBanner("Tasks keep their order; dates count from the start you pick.", title = "Good to know")
            InlineBanner("The chart has no year; weeks count from the start you pick.", kind = BannerKind.Warning, title = "Check these")
            InlineBanner("Sync failed: no connection", kind = BannerKind.Error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AuroraFab("New project", onClick = {}) { Icon(Icons.Filled.Add, contentDescription = null) }
            }
        }
    }
}

/** An empty page: the bars, a title, a sentence and a button. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun EmptyStateWithAction() = ChronovaTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        EmptyState(
            title = "No projects yet",
            body = "Plan your first one: give it a name and dates, then add tasks to its timeline.",
            action = { Button(onClick = {}) { Text("New project") } },
        )
    }
}

/** A sheet's content: title, a field, a banner and the buttons with a destructive one. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun SheetContent() = ChronovaTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        SheetColumn(Modifier.padding(top = 24.dp)) {
            SheetTitle("Edit task")
            OutlinedTextField(value = "Implementation", onValueChange = {}, label = { Text("Task title") }, modifier = Modifier.fillMaxWidth())
            InlineBanner("Give the task a title.", kind = BannerKind.Error)
            SheetActions(primaryLabel = "Save", onPrimary = {}, onCancel = {}, destructiveLabel = "Delete")
        }
    }
}

/** A row wider than the screen: its right edge fades while there is more to scroll to. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun FadingRow() = ChronovaTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        val scroll = rememberScrollState()
        Row(
            Modifier.fillMaxWidth().horizontalFadingEdges(scroll).horizontalScroll(scroll).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("All", "Sync", "Assistant", "Account", "Unread", "Reminders", "Imports").forEachIndexed { index, label ->
                FilterChip(selected = index == 0, onClick = {}, label = { Text(label) })
            }
        }
    }
}

/** One choice of a few: as wide as their words in a toolbar, sharing the width on a page. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ChoiceChipRows() = ChronovaTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceChips(options = listOf("Hours", "Days", "Weeks"), selected = "Days", label = { it }, onSelect = {})
            ChoiceChips(options = listOf("System", "Light", "Dark"), selected = "Light", label = { it }, onSelect = {}, fillWidth = true)
        }
    }
}
