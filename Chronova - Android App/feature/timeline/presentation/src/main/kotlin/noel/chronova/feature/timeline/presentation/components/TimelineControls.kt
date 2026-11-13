package noel.chronova.feature.timeline.presentation.components

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import noel.chronova.core.designsystem.ChronovaSpacing
import noel.chronova.core.designsystem.numeric
import noel.chronova.core.model.TimeScale
import noel.chronova.core.model.TimelineConfig
import noel.chronova.core.ui.components.horizontalFadingEdges
import noel.chronova.feature.timeline.presentation.R

/**
 * Configuration toolbar: time scale, zoom, snap interval, row height and the critical-path switch. It scrolls
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
    onCriticalPath: (Boolean) -> Unit = {},
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The row is wider than a phone: its edges fade while there is more to scroll to.
            .horizontalFadingEdges(scroll)
            .horizontalScroll(scroll)
            .padding(horizontal = ChronovaSpacing.screenGutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ControlGroup(stringResource(R.string.controls_scale)) {
            SegmentedChoice(
                options = TimeScale.entries,
                selected = config.timeScale,
                label = { scaleLabel(it) },
                onSelect = onTimeScale,
            )
        }
        ControlGroup(stringResource(R.string.controls_zoom)) {
            StepperIcon(MinusIcon, stringResource(R.string.controls_zoom_out), config.canZoomOut, onZoomOut)
            ValueText(stringResource(R.string.controls_zoom_value, config.zoomPercent))
            StepperIcon(Icons.Filled.Add, stringResource(R.string.controls_zoom_in), config.canZoomIn, onZoomIn)
            StepperIcon(Icons.Filled.Refresh, stringResource(R.string.controls_zoom_reset), config.zoomPercent != 100, onZoomReset)
        }
        ControlGroup(stringResource(R.string.controls_snap)) {
            SegmentedChoice(
                options = config.timeScale.snapOptions,
                selected = config.snapMinutes,
                label = { snapLabel(it) },
                onSelect = onSnap,
            )
        }
        ControlGroup(stringResource(R.string.controls_row_height)) {
            StepperIcon(MinusIcon, stringResource(R.string.controls_row_height_less), config.canShrinkRows) { onRowHeightStep(-1) }
            ValueText(stringResource(R.string.controls_row_height_value, config.rowHeightDp.toInt()))
            StepperIcon(Icons.Filled.Add, stringResource(R.string.controls_row_height_more), config.canGrowRows) { onRowHeightStep(+1) }
        }
        ControlGroup(stringResource(R.string.controls_critical_path)) {
            val description = stringResource(R.string.controls_critical_path_show)
            Switch(
                checked = config.showCriticalPath,
                onCheckedChange = onCriticalPath,
                modifier = Modifier
                    // 48 dp tall with its touch target, like the segmented buttons, so the row keeps its height.
                    .padding(horizontal = 4.dp)
                    .semantics { contentDescription = description },
            )
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
    label: @Composable (T) -> String,
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
        style = MaterialTheme.typography.labelLarge.numeric(),
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(min = 44.dp),
    )
}

@Composable
private fun scaleLabel(scale: TimeScale): String = stringResource(
    when (scale) {
        TimeScale.HOURS -> R.string.scale_hours
        TimeScale.DAYS -> R.string.scale_days
        TimeScale.WEEKS -> R.string.scale_weeks
        TimeScale.MONTHS -> R.string.scale_months
    },
)

/** 15m, 30m, 1h, 6h, 1d, 1w. */
@Composable
private fun snapLabel(minutes: Int): String = when {
    minutes % (7 * 24 * 60) == 0 -> stringResource(R.string.snap_weeks, minutes / (7 * 24 * 60))
    minutes % (24 * 60) == 0 -> stringResource(R.string.snap_days, minutes / (24 * 60))
    minutes % 60 == 0 -> stringResource(R.string.snap_hours, minutes / 60)
    else -> stringResource(R.string.snap_minutes, minutes)
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
