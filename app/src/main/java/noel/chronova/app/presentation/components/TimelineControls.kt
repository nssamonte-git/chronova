package noel.chronova.app.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import noel.chronova.app.domain.model.TimeScale
import noel.chronova.app.domain.model.TimelineConfig

/**
 * Configuration toolbar: time scale, zoom, snap interval and row height. It scrolls
 * horizontally so it fits narrow phones without wrapping.
 */
@Composable
fun TimelineControls(
    config: TimelineConfig,
    onTimeScale: (TimeScale) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onSnap: (Int) -> Unit,
    onRowHeightStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ControlGroup("Scale") {
            SegmentedChoice(
                options = TimeScale.entries,
                selected = config.timeScale,
                label = ::scaleLabel,
                onSelect = onTimeScale,
            )
        }
        ControlGroup("Zoom") {
            StepperIcon(MinusIcon, "Zoom out", config.canZoomOut, onZoomOut)
            ValueText("${config.zoomPercent}%")
            StepperIcon(Icons.Filled.Add, "Zoom in", config.canZoomIn, onZoomIn)
            StepperIcon(Icons.Filled.Refresh, "Reset zoom", config.zoomPercent != 100, onZoomReset)
        }
        ControlGroup("Snap") {
            SegmentedChoice(
                options = config.timeScale.snapOptions,
                selected = config.snapMinutes,
                label = ::snapLabel,
                onSelect = onSnap,
            )
        }
        ControlGroup("Row height") {
            StepperIcon(MinusIcon, "Decrease row height", config.canShrinkRows) { onRowHeightStep(-1) }
            ValueText("${config.rowHeightDp.toInt()}dp")
            StepperIcon(Icons.Filled.Add, "Increase row height", config.canGrowRows) { onRowHeightStep(+1) }
        }
    }
}

@Composable
private fun ControlGroup(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.padding(vertical = 4.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {}, // no checkmark: keeps the toolbar compact on phones
                modifier = Modifier.height(40.dp),
            ) {
                Text(label(option), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StepperIcon(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun ValueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(min = 44.dp),
    )
}

private fun scaleLabel(scale: TimeScale): String = when (scale) {
    TimeScale.HOURS -> "Hours"
    TimeScale.DAYS -> "Days"
    TimeScale.WEEKS -> "Weeks"
    TimeScale.MONTHS -> "Months"
}

/** 15m, 30m, 1h, 6h, 1d, 1w. */
private fun snapLabel(minutes: Int): String = when {
    minutes % (7 * 24 * 60) == 0 -> "${minutes / (7 * 24 * 60)}w"
    minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)}d"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes}m"
}

/** Material "remove" glyph; defined locally because it is only in the large extended icon set. */
private val MinusIcon: ImageVector = materialIcon(name = "Filled.Remove") {
    materialPath {
        moveTo(19f, 13f)
        horizontalLineTo(5f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(2f)
        close()
    }
}
