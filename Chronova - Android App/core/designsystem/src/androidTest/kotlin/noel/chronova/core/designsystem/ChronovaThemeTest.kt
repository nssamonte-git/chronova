package noel.chronova.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import noel.chronova.core.model.TaskColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The theme and task colours, composed on a device (this module's own test app). */
@RunWith(AndroidJUnit4::class)
class ChronovaThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeUsesTheBrandColours() {
        lateinit var scheme: ColorScheme
        compose.setContent { ChronovaTheme(darkTheme = false) { scheme = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        assertEquals(ChronovaBlue, scheme.primary)
        assertEquals(ChronovaGreen, scheme.secondary)
        assertEquals(ChronovaPurple, scheme.tertiary)
        assertEquals(ChronovaBackground, scheme.background)
        assertEquals(ChronovaSurface, scheme.surface)
        assertEquals(ChronovaText, scheme.onSurface)
        assertEquals(ChronovaOutline, scheme.outline)
        // Selected segments stay on-brand blue rather than Material's default.
        assertEquals(ChronovaBlueContainer, scheme.secondaryContainer)
    }

    @Test
    fun darkUsesLighterAccentsOnTheLogosNavy() {
        lateinit var scheme: ColorScheme
        compose.setContent { ChronovaTheme(darkTheme = true) { scheme = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        assertEquals(ChronovaDarkBlue, scheme.primary)
        assertEquals(ChronovaDarkGreen, scheme.secondary)
        assertEquals(ChronovaDarkPurple, scheme.tertiary)
        assertEquals(ChronovaDarkBackground, scheme.background)
        assertEquals(ChronovaDarkSurface, scheme.surface)
        assertEquals(ChronovaDarkText, scheme.onSurface)
        // Sheets and menus use the containers: navy too, not Material's default dark greys.
        assertEquals(ChronovaDarkSurfaceLow, scheme.surfaceContainerLow)
        assertEquals(ChronovaDarkSurfaceContainer, scheme.surfaceContainer)
    }

    @Test
    fun lightSheetsAndMenusUseTheAppsNeutrals() {
        lateinit var scheme: ColorScheme
        compose.setContent { ChronovaTheme(darkTheme = false) { scheme = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        // Not Material's default lavender containers.
        assertEquals(ChronovaSurfaceLow, scheme.surfaceContainerLow)
        assertEquals(ChronovaSurfaceContainer, scheme.surfaceContainer)
        assertEquals(ChronovaSurfaceHighest, scheme.surfaceContainerHighest)
    }

    @Test
    fun textAndAccentsAreReadableInBothThemes() {
        lateinit var light: ColorScheme
        lateinit var dark: ColorScheme
        compose.setContent {
            ChronovaTheme(darkTheme = false) { light = MaterialTheme.colorScheme }
            ChronovaTheme(darkTheme = true) { dark = MaterialTheme.colorScheme }
        }
        compose.waitForIdle()
        for (scheme in listOf(light, dark)) {
            // Text: WCAG AA, 4.5:1.
            assertTrue(contrast(scheme.onSurface, scheme.surface) >= 4.5f)
            assertTrue(contrast(scheme.onSurfaceVariant, scheme.surfaceContainerHighest) >= 4.5f)
            assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
            assertTrue(contrast(scheme.primary, scheme.background) >= 4.5f)
            assertTrue(contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
            assertTrue(contrast(scheme.onErrorContainer, scheme.errorContainer) >= 4.5f)
            // Field borders: 3:1.
            assertTrue(contrast(scheme.outline, scheme.surface) >= 3f)
        }
    }

    @Test
    fun whiteBarLabelsAreReadableOnEveryTaskColour() {
        // Bar labels are bold: WCAG's 3:1 for large text.
        TaskColor.entries.forEach { assertTrue(it.name, contrast(Color.White, it.toColor()) >= 3f) }
    }

    @Test
    fun snackbarActionsAreReadableInBothThemes() {
        lateinit var light: ColorScheme
        lateinit var dark: ColorScheme
        compose.setContent {
            ChronovaTheme(darkTheme = false) { light = MaterialTheme.colorScheme }
            ChronovaTheme(darkTheme = true) { dark = MaterialTheme.colorScheme }
        }
        compose.waitForIdle()
        // An action button's text (inversePrimary) on the snackbar (inverseSurface): WCAG AA, 4.5:1.
        for (scheme in listOf(light, dark)) {
            assertTrue(contrast(scheme.inversePrimary, scheme.inverseSurface) >= 4.5f)
            assertTrue(contrast(scheme.inverseOnSurface, scheme.inverseSurface) >= 4.5f)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun everyTaskColourIsItsOwnBrandColourInBothThemes() {
        lateinit var light: Map<TaskColor, Color>
        lateinit var dark: Map<TaskColor, Color>
        compose.setContent {
            ChronovaTheme(darkTheme = false) { light = TaskColor.entries.associateWith { it.toColor() } }
            ChronovaTheme(darkTheme = true) { dark = TaskColor.entries.associateWith { it.toColor() } }
        }
        compose.waitForIdle()
        // Dark's accents are lighter than the task tones; bars must not follow them.
        val brand = mapOf(
            TaskColor.BLUE to ChronovaBlue,
            TaskColor.GREEN to ChronovaGreen,
            TaskColor.PURPLE to ChronovaPurple,
            TaskColor.AMBER to ChronovaAmber,
        )
        assertEquals(brand, light)
        assertEquals(brand, dark)
        assertEquals(TaskColor.entries.size, light.values.toSet().size)
    }
}
