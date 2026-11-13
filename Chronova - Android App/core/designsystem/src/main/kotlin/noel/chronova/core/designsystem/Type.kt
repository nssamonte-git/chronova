package noel.chronova.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Inter 4.1 (SIL Open Font License, `docs/brand/inter/OFL.txt`), bundled as Latin subsets with
 * tabular numerals kept; other scripts fall back to the phone's own fonts. Three weights: the
 * scale uses Regular for reading, SemiBold for titles and labels, Bold for emphasis.
 */
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val Base = Typography()

private fun TextStyle.inter(weight: FontWeight, tracking: Float = 0f) = copy(
    fontFamily = InterFontFamily,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

/**
 * Material 3's sizes and line heights on Inter. Display and headline sizes are tightened a
 * little (Inter is wide at large sizes); titles and labels are SemiBold, so a screen title looks
 * the same on every screen without setting a weight where it is used.
 */
internal val ChronovaTypography = Typography(
    displayLarge = Base.displayLarge.inter(FontWeight.SemiBold, -0.02f),
    displayMedium = Base.displayMedium.inter(FontWeight.SemiBold, -0.02f),
    displaySmall = Base.displaySmall.inter(FontWeight.SemiBold, -0.015f),
    headlineLarge = Base.headlineLarge.inter(FontWeight.SemiBold, -0.015f),
    headlineMedium = Base.headlineMedium.inter(FontWeight.SemiBold, -0.01f),
    headlineSmall = Base.headlineSmall.inter(FontWeight.SemiBold, -0.01f),
    titleLarge = Base.titleLarge.inter(FontWeight.SemiBold, -0.01f),
    titleMedium = Base.titleMedium.inter(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.inter(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.inter(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.inter(FontWeight.Normal),
    bodySmall = Base.bodySmall.inter(FontWeight.Normal),
    labelLarge = Base.labelLarge.inter(FontWeight.SemiBold),
    labelMedium = Base.labelMedium.inter(FontWeight.SemiBold, 0.01f),
    labelSmall = Base.labelSmall.copy(fontSize = 11.sp).inter(FontWeight.SemiBold, 0.02f),
)

/**
 * The same style with tabular (equal-width) numerals, for times, dates, percentages and counts,
 * so a column of them lines up and a changing number doesn't shift its neighbours.
 */
fun TextStyle.numeric(): TextStyle = copy(fontFeatureSettings = "tnum")
