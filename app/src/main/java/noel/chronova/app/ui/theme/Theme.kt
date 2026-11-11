package noel.chronova.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ChronovaLightColors = lightColorScheme(
    primary = ChronovaBlue,
    onPrimary = Color.White,
    primaryContainer = ChronovaBlueContainer,
    onPrimaryContainer = ChronovaOnBlueContainer,
    secondary = ChronovaGreen,
    onSecondary = Color.White,
    // Segmented buttons use secondaryContainer for the selected state; keep it on-brand blue.
    secondaryContainer = ChronovaBlueContainer,
    onSecondaryContainer = ChronovaOnBlueContainer,
    tertiary = ChronovaPurple,
    onTertiary = Color.White,
    tertiaryContainer = ChronovaPurpleContainer,
    onTertiaryContainer = ChronovaOnPurpleContainer,
    background = ChronovaBackground,
    onBackground = ChronovaText,
    surface = ChronovaSurface,
    onSurface = ChronovaText,
    surfaceVariant = ChronovaSurfaceVariant,
    onSurfaceVariant = ChronovaOnSurfaceVariant,
    outline = ChronovaOutline,
    outlineVariant = ChronovaOutlineVariant,
)

@Composable
fun ChronovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChronovaLightColors,
        content = content,
    )
}
