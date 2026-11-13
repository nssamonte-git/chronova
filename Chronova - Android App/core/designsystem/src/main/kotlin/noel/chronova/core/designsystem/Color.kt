package noel.chronova.core.designsystem

import androidx.compose.ui.graphics.Color

// The "Aurora" palette, taken from the logo: a navy tile with a cyan → blue → violet hourglass.
// Contrast ratios quoted here were measured (WCAG relative luminance) when the palette was chosen.

// The logo's gradient stops. Used only through ChronovaExtendedColors.aurora, on small solid
// shapes (brand mark, primary FAB, the now line, progress edges), never behind body text.
val AuroraCyan = Color(0xFF22D3EE)
val AuroraBlue = Color(0xFF3B82F6)
val AuroraViolet = Color(0xFFA855F7)

// Brand and task tones: the same in both themes, so white bold labels on bars stay readable.
// White on each: blue 5.2:1, green 4.5:1, purple 5.7:1, amber 3.8:1 (bar labels are bold, ≥ 3:1).
val ChronovaBlue = Color(0xFF2B5CFF)
val ChronovaGreen = Color(0xFF0B8577)
val ChronovaPurple = Color(0xFF7C3AED)
val ChronovaAmber = Color(0xFFC96A00)

// Light theme: an airy near-white page, white cards, ink text.
val ChronovaBackground = Color(0xFFF7F8FC)
val ChronovaSurface = Color(0xFFFFFFFF)
val ChronovaText = Color(0xFF0B1226)
val ChronovaSurfaceLow = Color(0xFFF6F7FB)
val ChronovaSurfaceContainer = Color(0xFFF1F3F9)
val ChronovaSurfaceHigh = Color(0xFFEBEEF6)
val ChronovaSurfaceHighest = Color(0xFFE4E8F2)
val ChronovaOnSurfaceVariant = Color(0xFF4A5578)
// Field and control borders: 3:1 on white. The variant is decorative (dividers, card borders).
val ChronovaOutline = Color(0xFF8A94B2)
val ChronovaOutlineVariant = Color(0xFFE2E6F0)

val ChronovaBlueContainer = Color(0xFFE3EAFF)
val ChronovaOnBlueContainer = Color(0xFF0F2A8C)
val ChronovaPurpleContainer = Color(0xFFEFE7FF)
val ChronovaOnPurpleContainer = Color(0xFF3B1A8C)
val ChronovaErrorContainer = Color(0xFFFDE7E9)
val ChronovaOnErrorContainer = Color(0xFF8C1D18)
val ChronovaWarning = Color(0xFFB45309)
val ChronovaWarningContainer = Color(0xFFFFF4DB)
val ChronovaOnWarningContainer = Color(0xFF7A4B00)
val ChronovaSuccessContainer = Color(0xFFDDF5F0)
val ChronovaOnSuccessContainer = Color(0xFF0A5C52)
// Snackbars sit on the inverse surface; their action text needs its own lighter blue.
val ChronovaInverseSurface = Color(0xFF1B2440)
val ChronovaInverseOnSurface = Color(0xFFEEF1FA)
val ChronovaInversePrimary = Color(0xFFB5C7FF)

// Dark theme: the logo's navy, from deepest to most raised, with lighter accents so text and
// icons in the accent colours stay readable. Bars keep the fixed task tones above.
val ChronovaDarkBackground = Color(0xFF070B1A)
val ChronovaDarkSurface = Color(0xFF0B1226)
val ChronovaDarkSurfaceLow = Color(0xFF0D1429)
val ChronovaDarkSurfaceContainer = Color(0xFF111A33)
val ChronovaDarkSurfaceHigh = Color(0xFF17213F)
val ChronovaDarkSurfaceHighest = Color(0xFF1E2A4D)
val ChronovaDarkText = Color(0xFFE6EAF5)
val ChronovaDarkOnSurfaceVariant = Color(0xFF9AA6C6)
val ChronovaDarkOutline = Color(0xFF6373A6)
val ChronovaDarkOutlineVariant = Color(0xFF26305A)

val ChronovaDarkBlue = Color(0xFF8AA8FF)
val ChronovaDarkOnBlue = Color(0xFF0A1440)
val ChronovaDarkGreen = Color(0xFF3FD5C2)
val ChronovaDarkOnGreen = Color(0xFF00382F)
val ChronovaDarkPurple = Color(0xFFC4A6FF)
val ChronovaDarkOnPurple = Color(0xFF2A0F6B)
val ChronovaDarkBlueContainer = Color(0xFF1C2E73)
val ChronovaDarkOnBlueContainer = Color(0xFFDCE5FF)
val ChronovaDarkPurpleContainer = Color(0xFF3A2278)
val ChronovaDarkOnPurpleContainer = Color(0xFFEADDFF)
val ChronovaDarkErrorContainer = Color(0xFF4A1218)
val ChronovaDarkOnErrorContainer = Color(0xFFFFDAD6)
val ChronovaDarkWarning = Color(0xFFFFC56B)
val ChronovaDarkWarningContainer = Color(0xFF3A2A05)
val ChronovaDarkOnWarningContainer = Color(0xFFFFD98A)
val ChronovaDarkSuccessContainer = Color(0xFF0A3A34)
val ChronovaDarkOnSuccessContainer = Color(0xFF9FF0E0)
val ChronovaDarkInversePrimary = Color(0xFF1F47D6)

/**
 * The assistant's overlay is a focus mode on the logo's navy in both themes, so its colours
 * don't follow the theme.
 */
object AssistantPalette {
    val Scrim = Color(0xCC060B24)
    val Caption = Color.White
    val CaptionQuiet = Color.White.copy(alpha = 0.7f)
    val Hint = Color.White.copy(alpha = 0.55f)

    /** The fallback orb (no 3D) and the halo behind the orb: light at the centre, out through blue and violet. */
    val OrbCore = Color(0xFFBFE9FF)
    val OrbBlue = Color(0xFF5E7BFF)
    val OrbViolet = Color(0xFF8A3CF0)
    val Proposal = Color(0xFFBFD3FF)
    val Warning = Color(0xFFFFC56B)
    val Offer = Color(0xFF8EF0C4)
}
