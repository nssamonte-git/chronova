package noel.chronova.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import noel.chronova.core.model.TaskColor

/** Maps semantic task colors onto the theme; amber has no Material 3 role. */
@Composable
@ReadOnlyComposable
fun TaskColor.toColor(): Color = when (this) {
    TaskColor.BLUE -> MaterialTheme.colorScheme.primary
    TaskColor.GREEN -> MaterialTheme.colorScheme.secondary
    TaskColor.PURPLE -> MaterialTheme.colorScheme.tertiary
    TaskColor.AMBER -> ChronovaAmber
}
