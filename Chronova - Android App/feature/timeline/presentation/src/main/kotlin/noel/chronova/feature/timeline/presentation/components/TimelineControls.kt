package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import noel.chronova.core.designsystem.ChronovaSpacing
import noel.chronova.core.designsystem.numeric
import noel.chronova.core.model.TimeScale
import noel.chronova.core.model.TimelineConfig
import noel.chronova.core.ui.components.ChoiceChips
import noel.chronova.core.ui.components.horizontalFadingEdges
import noel.chronova.feature.timeline.presentation.R

/**
 * Configuration toolbar: time scale, zoom, snap interval, row height and the critical-path switch,
 * each with its name beside it, on one line. It scrolls horizontally so it fits narrow phones
 * without wrapping.
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
        horizontalArrangement = Arrangement.spacedBy(ChronovaSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlGroup(stringResource(R.string.controls_scale)) {
            ChoiceChips(
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
            ChoiceChips(
                options = config.timeScale.snapOptions,
                selected = config.snapMinutes,
                label = { snapLabel(it) },
                onSelect = onSnap,
                spoken = { snapSpoken(it) },
            )
        }
        ControlGroup(stringResource(R.string.controls_row_height)) {
            StepperIcon(MinusIcon, stringResource(R.string.controls_row_height_less), config.canShrinkRows) { onRowHeightStep(-1) }
            // As a share of the usual height, like zoom (100% is the default 56 dp row).
            ValueText(stringResource(R.string.controls_row_height_value, config.rowHeightPercent))
            StepperIcon(Icons.Filled.Add, stringResource(R.string.controls_row_height_more), config.canGrowRows) { onRowHeightStep(+1) }
        }
        ControlGroup(stringResource(R.string.controls_critical_path)) {
            val description = stringResource(R.string.controls_critical_path_show)
            Switch(
                checked = config.showCriticalPath,
                onCheckedChange = onCriticalPath,
                modifier = Modifier
                    // 48 dp tall with its touch target, like the chips, so the row keeps its height.
                    .padding(horizontal = 4.dp)
                    .semantics { contentDescription = description },
            )
        }
    }
}

/** A group's name and its controls on one line, so the toolbar is one row tall. */
@Composable
private fun ControlGroup(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = ChronovaSpacing.s),
        )
        content()
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

/** The same steps in words, for TalkBack: "15 minutes", "1 hour", "1 day", "1 week". */
@Composable
private fun snapSpoken(minutes: Int): String = when {
    minutes % (7 * 24 * 60) == 0 -> (minutes / (7 * 24 * 60)).let { pluralStringResource(R.plurals.snap_weeks_spoken, it, it) }
    minutes % (24 * 60) == 0 -> (minutes / (24 * 60)).let { pluralStringResource(R.plurals.snap_days_spoken, it, it) }
    minutes % 60 == 0 -> (minutes / 60).let { pluralStringResource(R.plurals.snap_hours_spoken, it, it) }
    else -> pluralStringResource(R.plurals.snap_minutes_spoken, minutes, minutes)
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
