package noel.chronova.feature.timeline.presentation.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 *
 * Progress darkens the bar from its start: [doneFraction] is the share of the
 * visible bar that is done (the caller works it out from the whole bar, which may
 * reach past the container). A [done] task also gets a check before its label.
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
    doneFraction: Float = 0f,
    done: Boolean = false,
    /** Shown after the label ("· 40%"), kept in view while the label shortens; read out by the bar's state instead. */
    progressLabel: String? = null,
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
                .drawBehind {
                    if (doneFraction > 0f) {
                        drawRect(DoneShade, size = Size(size.width * doneFraction.coerceAtMost(1f), size.height))
                    }
                }
                .then(if (selected) Modifier.border(2.dp, outline.copy(alpha = 0.55f), BarShape) else Modifier)
                .then(if (highlighted) Modifier.border(3.dp, highlightBrush(pulse), BarShape) else Modifier)
                .padding(horizontal = if (selected) 12.dp else 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 4.dp).size(16.dp),
                    )
                }
                Text(
                    text = label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (progressLabel != null) {
                    Text(
                        text = progressLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clearAndSetSemantics {},
                    )
                }
            }
        }
        if (selected) {
            // Grips centred on the bar's edges.
            ResizeGrip(color, Modifier.align(Alignment.CenterStart).offset(x = handleOutset - HandleWidth / 2))
            ResizeGrip(color, Modifier.align(Alignment.CenterEnd).offset(x = -(handleOutset - HandleWidth / 2)))
        }
    }
}

/**
 * A milestone: a diamond centred in a [MilestoneTouchWidth]-wide start area (the
 * moment sits at its centre), with [label] beside it. It has no grips: a milestone
 * only moves. A [done] milestone shows a check inside the diamond.
 */
@Composable
fun MilestoneMark(
    label: String,
    color: Color,
    selected: Boolean,
    active: Boolean,
    done: Boolean,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val outline = MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(MilestoneTouchWidth), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(DiamondSide)
                    .rotate(45f)
                    .shadow(if (highlighted) 8.dp else if (active) 6.dp else if (selected) 2.dp else 0.dp, DiamondShape)
                    .background(color, DiamondShape)
                    .then(if (selected) Modifier.border(2.dp, outline.copy(alpha = 0.55f), DiamondShape) else Modifier)
                    .then(if (highlighted) Modifier.border(3.dp, highlightBrush(1f), DiamondShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.rotate(-45f).size(12.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = MilestoneLabelWidth),
        )
    }
}

/** The milestone's touch area around its diamond (a comfortable target). */
val MilestoneTouchWidth = 48.dp

/** Room for a milestone's title after its diamond. */
val MilestoneLabelWidth = 160.dp

private val DiamondSide = 14.dp
private val DiamondShape = RoundedCornerShape(2.dp)

/** Darkens the done part of a bar; white labels stay readable on every task colour. */
private val DoneShade = Color.Black.copy(alpha = 0.22f)

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
