package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import noel.chronova.core.timeline.TimelineMath
import noel.chronova.feature.timeline.presentation.DependencyUi
import noel.chronova.feature.timeline.presentation.TaskUi

/**
 * Finish-to-start arrows over the bar lanes, from each predecessor's end to its
 * successor's start. Violated links (the successor starts before the predecessor
 * ends) are drawn in the error colour; with the critical path shown, links between
 * two critical tasks are darker and thicker.
 *
 * Everything is computed in the draw phase from the shared horizontal scroll and
 * the list's first visible row, so scrolling never recomposes. Rows have equal
 * heights, which lets arrows reach rows that are scrolled out of view.
 */
@Composable
fun DependencyArrows(
    tasks: List<TaskUi>,
    dependencies: List<DependencyUi>,
    originMillis: Long,
    pxPerMinute: Float,
    rowHeight: Dp,
    viewport: TimelineViewportState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    /** The critical-path switch is on: links between two critical tasks are drawn darker and thicker. */
    showCriticalPath: Boolean = false,
) {
    if (dependencies.isEmpty()) return
    val normal = MaterialTheme.colorScheme.onSurfaceVariant
    val critical = MaterialTheme.colorScheme.onSurface
    val violated = MaterialTheme.colorScheme.error
    val rows = remember(tasks) { tasks.withIndex().associate { (index, task) -> task.id to (index to task) } }

    Canvas(modifier.clipToBounds()) {
        val rowPx = rowHeight.roundToPx().toFloat()
        val scrollX = viewport.scrollX
        val scrollY = listState.firstVisibleItemIndex * rowPx + listState.firstVisibleItemScrollOffset
        val stub = 8.dp.toPx()
        val head = 6.dp.toPx()
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val criticalStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        for (link in dependencies) {
            val (fromRow, from) = rows[link.predecessorId] ?: continue
            val (toRow, to) = rows[link.successorId] ?: continue
            val startX = TimelineMath.timeToX(from.endMillis, originMillis, pxPerMinute) - scrollX
            val endX = TimelineMath.timeToX(to.startMillis, originMillis, pxPerMinute) - scrollX
            val startY = fromRow * rowPx + rowPx / 2 - scrollY
            val endY = toRow * rowPx + rowPx / 2 - scrollY
            // Skip links entirely off-screen.
            if (maxOf(startX, endX) + stub < 0f) continue
            if (minOf(startX, endX) - stub > size.width) continue

            val path = Path().apply {
                moveTo(startX, startY)
                if (endX - stub >= startX + stub) {
                    // Room for a simple elbow: out, down, in.
                    lineTo(startX + stub, startY)
                    lineTo(startX + stub, endY)
                } else {
                    // Successor starts before (or just after) the predecessor ends:
                    // go out, to the row boundary, back past the successor's start, then down.
                    val betweenY = if (endY > startY) toRow * rowPx - scrollY else (toRow + 1) * rowPx - scrollY
                    lineTo(startX + stub, startY)
                    lineTo(startX + stub, betweenY)
                    lineTo(endX - stub, betweenY)
                    lineTo(endX - stub, endY)
                }
                lineTo(endX, endY)
            }
            val onPath = showCriticalPath && link.critical
            val color = when {
                link.violated -> violated
                onPath -> critical
                else -> normal
            }
            drawPath(path, color, style = if (onPath) criticalStroke else stroke)
            // Arrowhead pointing right, into the successor's start.
            val arrow = Path().apply {
                moveTo(endX, endY)
                lineTo(endX - head, endY - head * 0.6f)
                lineTo(endX - head, endY + head * 0.6f)
                close()
            }
            drawPath(arrow, color)
        }
    }
}

