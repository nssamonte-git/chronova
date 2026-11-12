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

/** The fixed left cell of a timeline row: colour dot, title and time range. Tapping it edits the task. */
@Composable
fun TaskNameCell(
    task: TaskUi,
    zone: ZoneId,
    rowHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clickable(onClickLabel = stringResource(R.string.task_edit), onClick = onClick)) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(task.color.toColor(), CircleShape),
            )
            Column {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rowHeight >= 48.dp) {
                    Text(
                        text = TimeFormat.range(task.startMillis, task.endMillis, zone),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
