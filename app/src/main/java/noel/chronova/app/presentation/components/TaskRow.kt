package noel.chronova.app.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import kotlin.math.roundToInt
import noel.chronova.app.core.timeline.GestureMode
import noel.chronova.app.core.timeline.HandleHitTest
import noel.chronova.app.core.timeline.TaskEdits
import noel.chronova.app.core.timeline.TimelineMath
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TimelineWindow
import noel.chronova.app.presentation.TimeFormat
import noel.chronova.app.ui.theme.toColor

private val MinBarWidth = 4.dp

/** Touch area added outside each edge of a selected bar for its resize handle. */
private val HandleOutset = 20.dp

/** How far inside each edge the handle's touch area reaches (less on narrow bars). */
private val HandleInset = 20.dp

/**
 * One timeline row. A drag moves the bar, or — on the selected bar's edge
 * handles — resizes it. While a drag is in progress the row shows a local preview;
 * the ViewModel only receives the finished edit (on release), and a cancelled
 * gesture simply drops the preview.
 */
@Composable
fun TaskRow(
    task: SchedulerTask,
    selected: Boolean,
    window: TimelineWindow,
    zone: ZoneId,
    pxPerMinute: Float,
    snapMinutes: Int,
    rowHeight: Dp,
    onSelect: (String) -> Unit,
    onCommit: (SchedulerTask) -> Unit,
) {
    var preview by remember { mutableStateOf<SchedulerTask?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val shown = preview ?: task

    // After release the preview is kept until the committed task arrives from the
    // ViewModel; clearing it immediately would flash the bar back for one frame.
    LaunchedEffect(task) {
        if (!dragging) preview = null
    }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    // pointerInput is keyed on the task id only, so recompositions during a drag
    // (preview, config) don't restart the gesture; it reads the latest values here.
    val currentTask by rememberUpdatedState(task)
    val currentPxPerMinute by rememberUpdatedState(pxPerMinute)
    val currentSnapMillis by rememberUpdatedState(snapMinutes * TimelineMath.MILLIS_PER_MINUTE)
    val currentWindow by rememberUpdatedState(window)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnCommit by rememberUpdatedState(onCommit)

    val x = TimelineMath.timeToX(shown.startMillis, window.startMillis, pxPerMinute)
    val barWidthPx = TimelineMath.durationToWidth(shown.durationMillis, pxPerMinute)
        .coerceAtLeast(with(density) { MinBarWidth.toPx() })
    // Only the selected bar gets handles, and with them extra touch area on both sides.
    val outset = if (selected) HandleOutset else 0.dp
    val outsetPx = with(density) { outset.toPx() }
    val currentSelected by rememberUpdatedState(selected)
    val currentBarWidthPx by rememberUpdatedState(barWidthPx)

    Box(Modifier.fillMaxWidth().height(rowHeight)) {
        TaskBar(
            label = if (dragging) TimeFormat.range(shown.startMillis, shown.endMillis, zone) else task.title,
            color = task.color.toColor(),
            selected = selected,
            active = dragging,
            handleOutset = outset,
            modifier = Modifier
                // Layout-phase offset: moving a bar doesn't re-measure siblings.
                .offset { IntOffset((x - outsetPx).roundToInt(), 0) }
                .width(with(density) { (barWidthPx + 2 * outsetPx).toDp() })
                .pointerInput(task.id) {
                    var original = currentTask
                    var mode = GestureMode.MOVE
                    detectTaskGestures(
                        modeAt = { down ->
                            HandleHitTest.modeAt(
                                x = down.x,
                                barWidth = currentBarWidthPx,
                                outset = if (currentSelected) HandleOutset.toPx() else 0f,
                                inset = HandleInset.toPx(),
                                selected = currentSelected,
                            )
                        },
                        onTap = { currentOnSelect(currentTask.id) },
                        onDragStart = { startMode ->
                            original = currentTask
                            mode = startMode
                            dragging = true
                            currentOnSelect(original.id)
                        },
                        onDrag = { totalDx ->
                            val next = TaskEdits.apply(
                                mode = mode,
                                original = original,
                                deltaMillis = TimelineMath.pxToMillis(totalDx, currentPxPerMinute),
                                snapMillis = currentSnapMillis,
                                window = currentWindow,
                            )
                            val previous = preview ?: original
                            if (next.startMillis != previous.startMillis || next.endMillis != previous.endMillis) {
                                // Tick each time the snapped time changes.
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            preview = next
                        },
                        onDragEnd = {
                            dragging = false
                            val edited = preview?.takeIf { it != original }
                            if (edited != null) currentOnCommit(edited) else preview = null
                        },
                        onDragCancel = {
                            dragging = false
                            preview = null
                        },
                    )
                },
        )
    }
}
