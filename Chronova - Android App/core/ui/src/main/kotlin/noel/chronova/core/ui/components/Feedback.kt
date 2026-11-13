package noel.chronova.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import noel.chronova.core.designsystem.ChronovaTheme

/** What a banner is for; it picks the colours and the icon. */
enum class BannerKind { Info, Success, Warning, Error }

/**
 * A message in a tinted box, inside a page or a sheet: something to check, something that went
 * wrong, or a note. The text is the caller's; [content] adds lines or a button under it.
 */
@Composable
fun InlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    kind: BannerKind = BannerKind.Info,
    title: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val extended = ChronovaTheme.extended
    val (container, onContainer) = when (kind) {
        BannerKind.Info -> colors.primaryContainer to colors.onPrimaryContainer
        BannerKind.Success -> extended.successContainer to extended.onSuccessContainer
        BannerKind.Warning -> extended.warningContainer to extended.onWarningContainer
        BannerKind.Error -> colors.errorContainer to colors.onErrorContainer
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(container, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides onContainer) {
            Icon(
                when (kind) {
                    BannerKind.Info -> Icons.Filled.Info
                    BannerKind.Success -> Icons.Filled.CheckCircle
                    BannerKind.Warning, BannerKind.Error -> Icons.Filled.Warning
                },
                contentDescription = null,
                modifier = Modifier.padding(top = 1.dp).size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
                if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium)
                content?.invoke(this)
            }
        }
    }
}

/**
 * What a page shows when it has nothing to list: the logo's bars, a title, a sentence, and
 * optionally a button. Centre it in the space the list would take.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.padding(horizontal = 32.dp, vertical = 24.dp).widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .padding(bottom = 8.dp)
                .size(88.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AuroraBars(Modifier.size(width = 44.dp, height = 30.dp))
        }
        // The colour is set here: an empty page may be shown outside a Scaffold or Surface.
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Box(Modifier.padding(top = 8.dp)) { action() }
        }
    }
}

/** A coloured dot and a short status ("All changes synced"). */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}

/**
 * How far along something is: a thin track filled with the logo's gradient. Decorative; say
 * the number in text. It fills from empty the first time it is shown and glides to a new value
 * after that (at once when the phone's animations are off, and in previews).
 */
@Composable
fun ProgressMeter(fraction: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = ChronovaTheme.extended.auroraHorizontal
    // Saved, so a card scrolled away and back doesn't fill again.
    var shown by rememberSaveable { mutableStateOf(false) }
    val preview = LocalInspectionMode.current
    LaunchedEffect(Unit) { shown = true }
    val animated = animateFloatAsState(
        targetValue = if (shown || preview) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "progress",
    )
    Canvas(modifier) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = radius)
        // Read here, in the draw phase: the bar redraws as it moves without recomposing its card.
        val width = size.width * animated.value
        // At least a dot once anything is done, so 5% of a short track still shows.
        if (width > 0f) drawRoundRect(fill, size = Size(width.coerceAtLeast(size.height), size.height), cornerRadius = radius)
    }
}
