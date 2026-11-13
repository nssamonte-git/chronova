package noel.chronova.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Every surface container is set in both themes, so sheets, menus, dialogs and switches use the
 * app's own neutrals rather than Material's default (purple-tinted) ones.
 */
internal val ChronovaLightColors = lightColorScheme(
    primary = ChronovaBlue,
    onPrimary = Color.White,
    primaryContainer = ChronovaBlueContainer,
    onPrimaryContainer = ChronovaOnBlueContainer,
    secondary = ChronovaGreen,
    onSecondary = Color.White,
    // Segmented buttons and chips use secondaryContainer for the selected state; keep it on-brand blue.
    secondaryContainer = ChronovaBlueContainer,
    onSecondaryContainer = ChronovaOnBlueContainer,
    tertiary = ChronovaPurple,
    onTertiary = Color.White,
    tertiaryContainer = ChronovaPurpleContainer,
    onTertiaryContainer = ChronovaOnPurpleContainer,
    errorContainer = ChronovaErrorContainer,
    onErrorContainer = ChronovaOnErrorContainer,
    background = ChronovaBackground,
    onBackground = ChronovaText,
    surface = ChronovaSurface,
    onSurface = ChronovaText,
    surfaceVariant = ChronovaSurfaceHigh,
    onSurfaceVariant = ChronovaOnSurfaceVariant,
    surfaceTint = ChronovaBlue,
    surfaceDim = ChronovaSurfaceHighest,
    surfaceBright = ChronovaSurface,
    surfaceContainerLowest = ChronovaSurface,
    surfaceContainerLow = ChronovaSurfaceLow,
    surfaceContainer = ChronovaSurfaceContainer,
    surfaceContainerHigh = ChronovaSurfaceHigh,
    surfaceContainerHighest = ChronovaSurfaceHighest,
    inverseSurface = ChronovaInverseSurface,
    inverseOnSurface = ChronovaInverseOnSurface,
    inversePrimary = ChronovaInversePrimary,
    outline = ChronovaOutline,
    outlineVariant = ChronovaOutlineVariant,
)

/** The logo's navy, with lighter accents; bars keep the fixed task tones (see [toColor]). */
internal val ChronovaDarkColors = darkColorScheme(
    primary = ChronovaDarkBlue,
    onPrimary = ChronovaDarkOnBlue,
    primaryContainer = ChronovaDarkBlueContainer,
    onPrimaryContainer = ChronovaDarkOnBlueContainer,
    secondary = ChronovaDarkGreen,
    onSecondary = ChronovaDarkOnGreen,
    secondaryContainer = ChronovaDarkBlueContainer,
    onSecondaryContainer = ChronovaDarkOnBlueContainer,
    tertiary = ChronovaDarkPurple,
    onTertiary = ChronovaDarkOnPurple,
    tertiaryContainer = ChronovaDarkPurpleContainer,
    onTertiaryContainer = ChronovaDarkOnPurpleContainer,
    errorContainer = ChronovaDarkErrorContainer,
    onErrorContainer = ChronovaDarkOnErrorContainer,
    background = ChronovaDarkBackground,
    onBackground = ChronovaDarkText,
    surface = ChronovaDarkSurface,
    onSurface = ChronovaDarkText,
    surfaceVariant = ChronovaDarkSurfaceHigh,
    onSurfaceVariant = ChronovaDarkOnSurfaceVariant,
    surfaceTint = ChronovaDarkBlue,
    surfaceDim = ChronovaDarkBackground,
    surfaceBright = ChronovaDarkSurfaceHighest,
    surfaceContainerLowest = ChronovaDarkBackground,
    surfaceContainerLow = ChronovaDarkSurfaceLow,
    surfaceContainer = ChronovaDarkSurfaceContainer,
    surfaceContainerHigh = ChronovaDarkSurfaceHigh,
    surfaceContainerHighest = ChronovaDarkSurfaceHighest,
    inverseSurface = ChronovaDarkText,
    inverseOnSurface = ChronovaDarkSurface,
    // Snackbar actions (Undo, Push) sit on the light inverse surface, so they need a dark blue.
    inversePrimary = ChronovaDarkInversePrimary,
    outline = ChronovaDarkOutline,
    outlineVariant = ChronovaDarkOutlineVariant,
)

/**
 * Colours Material 3 has no role for. Everything that differs between light and dark lives here
 * or in the colour schemes, so composables never ask which theme is on.
 */
@Immutable
data class ChronovaExtendedColors(
    /** The logo's cyan → blue → violet stops; draw them with [auroraHorizontal] and friends. */
    val aurora: List<Color>,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** A wash over weekend columns on the timeline. */
    val weekendShade: Color,
    /** How strongly a task's colour tints a surface behind an icon (off-screen arrows, chips). */
    val taskTintAlpha: Float,
    /** A hairline along the top of a bar, so it lifts off the page. */
    val barHighlight: Color,
    /** How strong the soft glow around aurora shapes is; the logo's neon shows on navy. */
    val glowAlpha: Float,
) {
    /** Left to right. */
    val auroraHorizontal: Brush get() = Brush.horizontalGradient(aurora)

    /** Top to bottom. */
    val auroraVertical: Brush get() = Brush.verticalGradient(aurora)

    /** Top-left to bottom-right, as in the logo. */
    val auroraDiagonal: Brush get() = Brush.linearGradient(aurora, start = Offset.Zero, end = Offset.Infinite)
}

internal val ChronovaLightExtended = ChronovaExtendedColors(
    aurora = listOf(AuroraCyan, AuroraBlue, AuroraViolet),
    warning = ChronovaWarning,
    warningContainer = ChronovaWarningContainer,
    onWarningContainer = ChronovaOnWarningContainer,
    success = ChronovaGreen,
    successContainer = ChronovaSuccessContainer,
    onSuccessContainer = ChronovaOnSuccessContainer,
    weekendShade = ChronovaText.copy(alpha = 0.03f),
    taskTintAlpha = 0.14f,
    barHighlight = Color.White.copy(alpha = 0.16f),
    glowAlpha = 0.08f,
)

internal val ChronovaDarkExtended = ChronovaExtendedColors(
    aurora = listOf(AuroraCyan, AuroraBlue, AuroraViolet),
    warning = ChronovaDarkWarning,
    warningContainer = ChronovaDarkWarningContainer,
    onWarningContainer = ChronovaDarkOnWarningContainer,
    success = ChronovaDarkGreen,
    successContainer = ChronovaDarkSuccessContainer,
    onSuccessContainer = ChronovaDarkOnSuccessContainer,
    weekendShade = Color.White.copy(alpha = 0.03f),
    taskTintAlpha = 0.22f,
    barHighlight = Color.White.copy(alpha = 0.20f),
    glowAlpha = 0.16f,
)

private val LocalChronovaExtendedColors = staticCompositionLocalOf { ChronovaLightExtended }

/** Light or dark; by default like the phone (the app passes the user's System / Light / Dark choice). */
@Composable
fun ChronovaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalChronovaExtendedColors provides if (darkTheme) ChronovaDarkExtended else ChronovaLightExtended,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) ChronovaDarkColors else ChronovaLightColors,
            typography = ChronovaTypography,
            shapes = ChronovaShapes,
            content = content,
        )
    }
}

/** The theme's values beyond Material's: `ChronovaTheme.extended`, `ChronovaTheme.spacing`. */
object ChronovaTheme {
    val extended: ChronovaExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalChronovaExtendedColors.current

    val spacing: ChronovaSpacing get() = ChronovaSpacing
}
