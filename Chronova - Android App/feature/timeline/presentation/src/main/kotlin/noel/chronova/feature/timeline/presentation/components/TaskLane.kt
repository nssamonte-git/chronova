package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import noel.chronova.core.designsystem.toColor
import noel.chronova.core.model.TimelineWindow
import noel.chronova.core.timeline.GestureMode
import noel.chronova.core.timeline.HandleHitTest
import noel.chronova.core.timeline.SnapGrid
import noel.chronova.core.timeline.TaskEdits
import noel.chronova.core.timeline.TimelineMath
import noel.chronova.core.timeline.ViewportMath
import noel.chronova.core.ui.TimeFormat
import noel.chronova.feature.timeline.presentation.TaskUi

private val MinBarWidth = 4.dp

/** Touch area added outside each edge of a selected bar for its resize handle. */
private val HandleOutset = 20.dp

/** How far inside each edge the handle's touch area reaches (less on narrow bars). */
private val HandleInset = 20.dp

/**
 * The scrolling part of one timeline row. A drag moves the bar, or — on the
 * selected bar's edge handles — resizes it. While a drag is in progress the lane
 * shows a local preview; the ViewModel only receives the finished edit (on
 * release), and a cancelled gesture simply drops the preview.
 *
 * Long bars are never measured at full width: the bar's touch container covers
 * only the part inside the viewport's compose span (see [ViewportMath.composeSpan]),
 * and it is positioned against the scroll in the layout phase.
 */
@Composable
fun TaskLane(
    task: TaskUi,
    selected: Boolean,
    window: TimelineWindow,
    zone: ZoneId,
    pxPerMinute: Float,
    snapMinutes: Int,
    viewport: TimelineViewportState,
    onSelect: (String) -> Unit,
    /** A finished drag: the task as it was when the drag started, and as edited. */
    onCommit: (original: TaskUi, edited: TaskUi) -> Unit,
    modifier: Modifier = Modifier,
    /** Just changed by the assistant. */
    highlighted: Boolean = false,
) {
    var preview by remember { mutableStateOf<TaskUi?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val shown = preview ?: task

    // After release the preview is kept until the committed task arrives from the
    // repository; clearing it immediately would flash the bar back for one frame.
    LaunchedEffect(task) {
        if (!dragging) preview = null
    }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    // pointerInput is keyed on the task id only, so recompositions during a drag
    // (preview, config, scrolling) don't restart the gesture; it reads the latest values here.
    val currentTask by rememberUpdatedState(task)
    val currentPxPerMinute by rememberUpdatedState(pxPerMinute)
    val grid = remember(snapMinutes, window.startMillis, zone) { SnapGrid.of(snapMinutes, window.startMillis, zone) }
    val currentGrid by rememberUpdatedState(grid)
    val currentWindow by rememberUpdatedState(window)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnCommit by rememberUpdatedState(onCommit)

    val barStart = TimelineMath.timeToX(shown.startMillis, window.startMillis, pxPerMinute)
    val barEnd = barStart + TimelineMath.durationToWidth(shown.durationMillis, pxPerMinute)
        .coerceAtLeast(with(density) { MinBarWidth.toPx() })
    // Only the selected bar gets handles, and with them extra touch area on both sides.
    val outsetPx = with(density) { if (selected) HandleOutset.toPx() else 0f }

    // Changes only when the scroll crosses a viewport-wide page, not every frame.
    val span by remember(viewport) {
        derivedStateOf { ViewportMath.composeSpan(viewport.scrollX, viewport.viewportWidth) }
    }
    val visibleStart = max(barStart, span.start)
    val visibleEnd = min(barEnd, span.end)
    val containerStart = visibleStart - outsetPx

    val currentSelected by rememberUpdatedState(selected)
    val currentBarStart by rememberUpdatedState(barStart)
    val currentBarEnd by rememberUpdatedState(barEnd)
    val currentContainerStart by rememberUpdatedState(containerStart)

    Box(modifier.clipToBounds()) {
        if (visibleEnd <= visibleStart) return@Box // nowhere near the viewport
        TaskBar(
            label = if (dragging) TimeFormat.range(shown.startMillis, shown.endMillis, zone) else task.title,
            color = task.color.toColor(),
            selected = selected,
            active = dragging,
            highlighted = highlighted,
            handleOutset = if (selected) HandleOutset else 0.dp,
            modifier = Modifier
                // Layout-phase reads: scrolling and moving don't recompose or re-measure siblings.
                .offset { IntOffset((containerStart - viewport.scrollX).roundToInt(), 0) }
                // The container is usually wider than the lane (it spans up to four
                // viewports); without this, `width` would be clamped to the lane width.
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(with(density) { (visibleEnd - visibleStart + 2 * outsetPx).toDp() })
                .pointerInput(task.id) {
                    var original = currentTask
                    var mode = GestureMode.MOVE
                    detectTaskGestures(
                        modeAt = { down ->
                            HandleHitTest.modeAt(
                                x = currentContainerStart + down.x,
                                barStart = currentBarStart,
                                barEnd = currentBarEnd,
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
                            val next = original.withSpan(
                                TaskEdits.apply(
                                    mode = mode,
                                    original = original.span,
                                    deltaMillis = TimelineMath.pxToMillis(totalDx, currentPxPerMinute),
                                    grid = currentGrid,
                                    window = currentWindow,
                                ),
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
                            if (edited != null) currentOnCommit(original, edited) else preview = null
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
