package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import noel.chronova.core.designsystem.toColor
import noel.chronova.core.ui.TimeFormat
import noel.chronova.feature.timeline.presentation.R
import noel.chronova.feature.timeline.presentation.TaskUi

/**
 * The fixed left cell of a timeline row: colour dot (a check once done), title, and in
 * taller rows the time range (one time for a milestone). Tapping it edits the task.
 * While the critical path is shown, a critical task gets a red mark at the start.
 */
@Composable
fun TaskNameCell(
    task: TaskUi,
    zone: ZoneId,
    rowHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The critical-path switch is on: a red mark at the start of a critical task's cell. */
    showCriticalPath: Boolean = false,
) {
    Box(modifier.clickable(onClickLabel = stringResource(R.string.task_edit), onClick = onClick)) {
        if (showCriticalPath && task.critical) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 3.dp)
                    .size(width = 3.dp, height = 20.dp)
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (task.isDone) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.task_bar_state_done),
                    tint = task.color.toColor(),
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(task.color.toColor(), CircleShape),
                )
            }
            Column {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rowHeight >= 48.dp) {
                    val times = if (task.isMilestone) {
                        TimeFormat.clock(task.startMillis, zone)
                    } else {
                        TimeFormat.span(task.startMillis, task.endMillis, zone)
                    }
                    // Times (or the days of a whole-day task) only: the cell is narrow, and the bar shows the progress.
                    Text(
                        text = times,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
