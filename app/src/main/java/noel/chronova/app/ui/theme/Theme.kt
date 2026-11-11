package noel.chronova.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ChronovaLightColors = lightColorScheme(
    primary = ChronovaBlue,
    onPrimary = Color.White,
    secondary = ChronovaGreen,
    onSecondary = Color.White,
    tertiary = ChronovaPurple,
    onTertiary = Color.White,
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
