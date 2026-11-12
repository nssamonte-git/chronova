package noel.chronova.app.presentation.voice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import noel.chronova.app.R
import noel.chronova.app.domain.voice.PreviewItem
import noel.chronova.app.domain.voice.ScheduleWarning
import noel.chronova.app.presentation.TimeFormat
import noel.chronova.app.voice.orb.OrbMode

/**
 * The voice assistant in the middle of the screen: the large orb over a dimmed
 * timeline, with read-only captions under it (what it heard, what it's saying,
 * and the changes it is asking about). There is nothing to type or press: tapping
 * outside the orb, or Back, stops it.
 */
@Composable
fun AssistantOverlay(
    state: AssistantUiState,
    zone: ZoneId,
    level: () -> Float,
    onClose: () -> Unit,
) {
    BackHandler(enabled = state.open, onBack = onClose)
    AnimatedVisibility(visible = state.open, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC060B24))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AnimatedVisibility(visible = state.open, enter = scaleIn(initialScale = 0.4f) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    val orbLabel = stringResource(
                        when (state.mode) {
                            OrbMode.Listening -> R.string.assistant_state_listening
                            OrbMode.Thinking -> R.string.assistant_state_thinking
                            OrbMode.Speaking -> R.string.assistant_state_speaking
                            OrbMode.Idle -> R.string.assistant_open
                        },
                    )
                    AssistantOrb(
                        mode = state.mode,
                        level = level,
                        modifier = Modifier
                            .size(180.dp)
                            .semantics { contentDescription = orbLabel },
                    ) {
                        // Without 3D: a plain glowing circle in the orb's colours.
                        Box(
                            Modifier
                                .size(150.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.radialGradient(
                                        listOf(Color(0xFFBFE9FF), Color(0xFF5E7BFF), Color(0xFF8A3CF0), Color(0x00000000)),
                                    ),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                ),
                        )
                    }
                }
                Captions(state, zone)
                Text(
                    stringResource(R.string.assistant_tap_to_close),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun Captions(state: AssistantUiState, zone: ZoneId) {
    Column(
        Modifier
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.heard?.let {
            Text(
                "“$it”",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
        state.saying?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
        }
        state.proposal.forEach { item ->
            Text(
                previewText(item, zone),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFBFD3FF),
                textAlign = TextAlign.Center,
            )
        }
        state.warnings.forEach { warning ->
            Text(
                warningText(warning),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFC56B),
                textAlign = TextAlign.Center,
            )
        }
        state.offer.forEach { item ->
            Text(
                stringResource(R.string.assistant_offer_line, previewText(item, zone)),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8EF0C4),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One clash a proposal would cause, as a caption line. */
@Composable
private fun warningText(warning: ScheduleWarning): String = when (warning) {
    is ScheduleWarning.Overlap -> stringResource(R.string.assistant_warning_overlap, warning.task, warning.other)
    is ScheduleWarning.BrokenLink -> stringResource(R.string.assistant_warning_link, warning.successor, warning.predecessor)
}

/** The one-time notice before the first conversation: what goes to ElevenLabs and OpenAI. */
@Composable
fun AssistantNotice(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.assistant_notice_title)) },
        text = { Text(stringResource(R.string.assistant_notice_body)) },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.assistant_notice_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun previewText(item: PreviewItem, zone: ZoneId): String = when (item) {
    is PreviewItem.Changed -> {
        val parts = mutableListOf<String>()
        if (item.renamed) parts += stringResource(R.string.assistant_preview_renamed, item.before.title, item.after.title)
        if (item.moved) {
            parts += stringResource(
                R.string.assistant_preview_moved,
                item.after.title,
                range(item.before.startMillis, item.before.endMillis, zone),
                range(item.after.startMillis, item.after.endMillis, zone),
            )
        }
        parts.joinToString("\n")
    }
    is PreviewItem.Added -> stringResource(R.string.assistant_preview_added, item.task.title, range(item.task.startMillis, item.task.endMillis, zone))
    is PreviewItem.Removed -> stringResource(R.string.assistant_preview_removed, item.task.title)
    is PreviewItem.Linked -> stringResource(R.string.assistant_preview_linked, item.predecessor, item.successor)
    is PreviewItem.Unlinked -> stringResource(R.string.assistant_preview_unlinked, item.predecessor, item.successor)
}

/** "Thu 12 09:00 – 11:00", or "Thu 12 09:00 – Fri 13 17:00" across days. */
private fun range(start: Long, end: Long, zone: ZoneId): String {
    val startDay = TimeFormat.shortDay(start, zone)
    val endDay = TimeFormat.shortDay(end - 1, zone)
    return if (startDay == endDay) {
        "$startDay ${TimeFormat.range(start, end, zone)}"
    } else {
        "$startDay ${TimeFormat.clock(start, zone)} – $endDay ${TimeFormat.clock(end, zone)}"
    }
}
