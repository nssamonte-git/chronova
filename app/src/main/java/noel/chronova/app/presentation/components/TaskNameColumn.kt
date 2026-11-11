package noel.chronova.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.presentation.TimeFormat
import noel.chronova.app.ui.theme.toColor

/** Fixed left column: one row per task, aligned with the timeline rows. */
@Composable
fun TaskNameColumn(
    tasks: List<SchedulerTask>,
    zone: ZoneId,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        tasks.forEach { task ->
            Box(Modifier.fillMaxWidth().height(rowHeight)) {
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
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomStart),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}
