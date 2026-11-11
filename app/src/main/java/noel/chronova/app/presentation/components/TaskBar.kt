package noel.chronova.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val BarShape = RoundedCornerShape(8.dp)

/**
 * A rounded task bar. Position and width are applied by the caller's [modifier].
 * Selected bars get an outline; the bar being dragged is raised and shows [label]
 * (its live start–end time) instead of the title.
 */
@Composable
fun TaskBar(
    label: String,
    color: Color,
    selected: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .shadow(elevation = if (active) 6.dp else if (selected) 2.dp else 0.dp, shape = BarShape)
            .clip(BarShape)
            .background(color)
            .then(if (selected) Modifier.border(2.dp, outline.copy(alpha = 0.55f), BarShape) else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
