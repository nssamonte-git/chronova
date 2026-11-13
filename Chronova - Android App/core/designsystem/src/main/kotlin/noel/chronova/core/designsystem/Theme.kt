package noel.chronova.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val ChronovaLightColors = lightColorScheme(
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

/**
 * The same brand colours on slate. Every surface container is set, so sheets, menus and
 * dialogs stay slate rather than Material's default (purple-tinted) dark greys.
 */
internal val ChronovaDarkColors = darkColorScheme(
    primary = ChronovaBlue,
    onPrimary = Color.White,
    primaryContainer = ChronovaDarkBlueContainer,
    onPrimaryContainer = ChronovaDarkOnBlueContainer,
    secondary = ChronovaGreen,
    onSecondary = Color.White,
    secondaryContainer = ChronovaDarkBlueContainer,
    onSecondaryContainer = ChronovaDarkOnBlueContainer,
    tertiary = ChronovaPurple,
    onTertiary = Color.White,
    tertiaryContainer = ChronovaDarkPurpleContainer,
    onTertiaryContainer = ChronovaDarkOnPurpleContainer,
    background = ChronovaDarkBackground,
    onBackground = ChronovaDarkText,
    surface = ChronovaDarkSurface,
    onSurface = ChronovaDarkText,
    surfaceVariant = ChronovaDarkSurfaceHigh,
    onSurfaceVariant = ChronovaDarkOnSurfaceVariant,
    surfaceTint = ChronovaBlue,
    surfaceDim = ChronovaDarkBackground,
    surfaceBright = ChronovaDarkSurfaceHighest,
    surfaceContainerLowest = ChronovaDarkBackground,
    surfaceContainerLow = ChronovaDarkSurfaceLow,
    surfaceContainer = ChronovaDarkSurfaceContainer,
    surfaceContainerHigh = ChronovaDarkSurfaceHigh,
    surfaceContainerHighest = ChronovaDarkSurfaceHighest,
    inverseSurface = ChronovaDarkText,
    inverseOnSurface = ChronovaDarkSurface,
    // Snackbar actions (Undo, Push) sit on the light inverse surface, so they need the dark blue.
    inversePrimary = ChronovaOnBlueContainer,
    outline = ChronovaDarkOutline,
    outlineVariant = ChronovaDarkOutlineVariant,
)

/** Light or dark; by default like the phone (the app passes the user's System / Light / Dark choice). */
@Composable
fun ChronovaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) ChronovaDarkColors else ChronovaLightColors,
        content = content,
    )
}
