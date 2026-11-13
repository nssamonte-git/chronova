package noel.chronova.core.designsystem

import androidx.compose.ui.graphics.Color
import noel.chronova.core.model.TaskColor

/**
 * A task's colour on screen. The four tones are the same in light and dark (they are not theme
 * roles: dark's accents are lighter, and would wash out a bar's white label).
 */
fun TaskColor.toColor(): Color = when (this) {
    TaskColor.BLUE -> ChronovaBlue
    TaskColor.GREEN -> ChronovaGreen
    TaskColor.PURPLE -> ChronovaPurple
    TaskColor.AMBER -> ChronovaAmber
}
