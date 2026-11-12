package noel.chronova.app.presentation.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BarShape = RoundedCornerShape(8.dp)
private val HandleShape = RoundedCornerShape(50)
private val HandleWidth = 8.dp
private val HandleHeight = 24.dp

/**
 * A task bar inside its touch container. The caller's [modifier] positions and sizes
 * the container; the visible bar is inset by [handleOutset] on each side, which is
 * the extra touch area given to the resize handles of a selected bar.
 *
 * Selected bars get an outline and a grip on each edge; the bar being edited is
 * raised and shows [label] (its live start–end time) instead of the title.
 */
@Composable
fun TaskBar(
    label: String,
    color: Color,
    selected: Boolean,
    active: Boolean,
    handleOutset: Dp,
    modifier: Modifier = Modifier,
    /** Just changed by the assistant: a pulsing outline in the orb's colours. */
    highlighted: Boolean = false,
) {
    val outline = MaterialTheme.colorScheme.onSurface
    val pulse = if (highlighted) {
        rememberInfiniteTransition(label = "highlight").animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "highlightAlpha",
        ).value
    } else {
        0f
    }
    Box(modifier.fillMaxHeight()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = handleOutset, vertical = 8.dp)
                .shadow(
                    elevation = if (highlighted) 8.dp else if (active) 6.dp else if (selected) 2.dp else 0.dp,
                    shape = BarShape,
                    ambientColor = if (highlighted) HighlightGlow else Color.Black,
                    spotColor = if (highlighted) HighlightGlow else Color.Black,
                )
                .clip(BarShape)
                .background(color)
                .then(if (selected) Modifier.border(2.dp, outline.copy(alpha = 0.55f), BarShape) else Modifier)
                .then(if (highlighted) Modifier.border(3.dp, highlightBrush(pulse), BarShape) else Modifier)
                .padding(horizontal = if (selected) 12.dp else 8.dp),
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
        if (selected) {
            // Grips centred on the bar's edges.
            ResizeGrip(color, Modifier.align(Alignment.CenterStart).offset(x = handleOutset - HandleWidth / 2))
            ResizeGrip(color, Modifier.align(Alignment.CenterEnd).offset(x = -(handleOutset - HandleWidth / 2)))
        }
    }
}

@Composable
private fun ResizeGrip(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(HandleWidth, HandleHeight)
            .shadow(1.dp, HandleShape)
            .background(Color.White, HandleShape)
            .border(2.dp, color, HandleShape),
    )
}

/** The assistant orb's colours: cyan → violet → magenta. */
private val HighlightGlow = Color(0xFF7A5CFF)

private fun highlightBrush(alpha: Float) = Brush.linearGradient(
    listOf(Color(0xFF00C8F0), Color(0xFF7A5CFF), Color(0xFFD14BE8)).map { it.copy(alpha = alpha) },
)
