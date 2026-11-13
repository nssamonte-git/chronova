package noel.chronova.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Pick one of a few short options ("Hours / Days / Weeks / Months"): a row of chips, each as
 * wide as its word, the chosen one filled. The fill and the text change colour smoothly when
 * the choice moves. With [fillWidth] the chips share the row's width equally (a setting on a
 * page); without it they take only the room they need (a toolbar).
 */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    Row(
        (if (fillWidth) modifier.fillMaxWidth() else modifier).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            ChoiceChip(label(option), selected = option == selected, fillWidth = fillWidth, onClick = { onSelect(option) })
        }
    }
}

@Composable
private fun RowScope.ChoiceChip(text: String, selected: Boolean, fillWidth: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.secondaryContainer else Color.Transparent, label = "chipContainer")
    val content by animateColorAsState(if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, label = "chipContent")
    val border by animateColorAsState(if (selected) Color.Transparent else colors.outlineVariant, label = "chipBorder")
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .then(if (fillWidth) Modifier.weight(1f) else Modifier)
            // The chip is 36 dp tall; the touch target around it is the usual 48.
            .minimumInteractiveComponentSize()
            // At least 36 dp tall: it grows with large text rather than cutting it.
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            // Sharing a row's width, a chip's size is already set: keep the room for its word
            // (at 1.5× text "System" didn't fit with the wider padding).
            .padding(horizontal = if (fillWidth) 6.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
