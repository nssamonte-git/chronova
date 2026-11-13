package noel.chronova.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import noel.chronova.core.model.TaskColor

/** The theme's colour roles, the aurora gradient and the four task colours with a bar label. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ThemeColours() = ChronovaTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        val scheme = MaterialTheme.colorScheme
        val extended = ChronovaTheme.extended
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(12.dp).background(extended.auroraHorizontal, RoundedCornerShape(6.dp)))
            listOf(
                "primary" to scheme.primary,
                "secondary" to scheme.secondary,
                "tertiary" to scheme.tertiary,
                "primary container" to scheme.primaryContainer,
                "surface" to scheme.surface,
                "sheet (container low)" to scheme.surfaceContainerLow,
                "container highest" to scheme.surfaceContainerHighest,
                "outline" to scheme.outline,
                "outline variant" to scheme.outlineVariant,
                "error" to scheme.error,
                "error container" to scheme.errorContainer,
                "warning container" to extended.warningContainer,
                "success container" to extended.successContainer,
            ).forEach { (name, colour) -> Swatch(name, colour) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskColor.entries.forEach { BarSample(it) }
            }
        }
    }
}

/** The type scale on Inter, with a line of tabular numerals. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ThemeType() = ChronovaTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        val type = MaterialTheme.typography
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Headline small", style = type.headlineSmall)
            Text("Title large", style = type.titleLarge)
            Text("Title medium", style = type.titleMedium)
            Text("Title small", style = type.titleSmall)
            Text("Body large: plan the week ahead.", style = type.bodyLarge)
            Text("Body medium: plan the week ahead.", style = type.bodyMedium)
            Text("Body small: plan the week ahead.", style = type.bodySmall)
            Text("Label large", style = type.labelLarge)
            Text("Label medium", style = type.labelMedium)
            Text("Label small", style = type.labelSmall)
            Text("09:15 – 11:45 · 40%", style = type.bodyMedium.numeric())
            Text("11:11 – 17:01 · 71%", style = type.bodyMedium.numeric())
        }
    }
}

@Composable
private fun Swatch(name: String, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).background(colour, RoundedCornerShape(6.dp)))
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A task colour as it is used: a bar with a white bold label. */
@Composable
private fun BarSample(color: TaskColor) {
    Box(
        Modifier.background(color.toColor(), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            color.name.lowercase(),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
