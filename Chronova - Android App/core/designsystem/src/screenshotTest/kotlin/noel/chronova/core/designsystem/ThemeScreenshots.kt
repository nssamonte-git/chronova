package noel.chronova.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import noel.chronova.core.model.TaskColor

/** The theme's colour roles and the four task colours, as labelled swatches. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ThemeColours() = ChronovaTheme {
    Surface {
        val scheme = MaterialTheme.colorScheme
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "primary" to scheme.primary,
                "secondary" to scheme.secondary,
                "tertiary" to scheme.tertiary,
                "primary container" to scheme.primaryContainer,
                "secondary container" to scheme.secondaryContainer,
                "surface variant" to scheme.surfaceVariant,
                "outline" to scheme.outline,
                "error" to scheme.error,
            ).forEach { (name, colour) -> Swatch(name, colour) }
            TaskColor.entries.forEach { Swatch("task ${it.name.lowercase()}", it.toColor()) }
        }
    }
}

@Composable
private fun Swatch(name: String, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).background(colour))
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}
