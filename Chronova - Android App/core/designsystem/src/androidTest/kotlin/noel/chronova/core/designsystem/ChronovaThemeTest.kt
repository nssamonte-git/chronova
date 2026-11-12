package noel.chronova.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import noel.chronova.core.model.TaskColor
import org.junit.Assert.assertEquals
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
        compose.setContent { ChronovaTheme { scheme = MaterialTheme.colorScheme } }
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
    fun everyTaskColourIsItsOwnBrandColour() {
        lateinit var colors: Map<TaskColor, Color>
        compose.setContent { ChronovaTheme { colors = TaskColor.entries.associateWith { it.toColor() } } }
        compose.waitForIdle()
        assertEquals(
            mapOf(
                TaskColor.BLUE to ChronovaBlue,
                TaskColor.GREEN to ChronovaGreen,
                TaskColor.PURPLE to ChronovaPurple,
                TaskColor.AMBER to ChronovaAmber,
            ),
            colors,
        )
        assertEquals(TaskColor.entries.size, colors.values.toSet().size)
    }
}
