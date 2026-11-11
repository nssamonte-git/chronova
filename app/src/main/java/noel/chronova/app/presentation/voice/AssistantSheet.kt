package noel.chronova.app.presentation.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import noel.chronova.app.R
import noel.chronova.app.domain.TaskProblem
import noel.chronova.app.domain.schedule.CommandProblem
import noel.chronova.app.domain.voice.PreviewItem
import noel.chronova.app.presentation.TimeFormat

/**
 * The assistant: type an instruction, see what it would change, then Apply or
 * Cancel. Nothing is written before Apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(
    state: AssistantUiState,
    zone: ZoneId,
    projectEnd: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val phase = state.phase
    val busy = phase is AssistantPhase.Thinking || phase is AssistantPhase.Applying
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.assistant_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                enabled = !busy,
                placeholder = { Text(stringResource(R.string.assistant_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            when (phase) {
                AssistantPhase.Thinking, AssistantPhase.Applying -> {
                    Text(stringResource(R.string.assistant_thinking), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is AssistantPhase.Question -> Text(phase.text, style = MaterialTheme.typography.bodyLarge)
                is AssistantPhase.Problem -> Text(
                    problemText(phase.problem, projectEnd),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                is AssistantPhase.Preview -> {
                    Text(stringResource(R.string.assistant_preview_title), style = MaterialTheme.typography.titleSmall)
                    Column(
                        Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        phase.items.forEach { item -> Text("• " + previewText(item, zone), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                AssistantPhase.Idle -> Unit
            }

            Text(
                stringResource(R.string.assistant_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                when (phase) {
                    AssistantPhase.Thinking -> TextButton(onClick = onCancel) { Text(stringResource(R.string.assistant_cancel)) }
                    is AssistantPhase.Preview -> {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.assistant_cancel)) }
                        Button(onClick = onApply) { Text(stringResource(R.string.assistant_apply)) }
                    }
                    AssistantPhase.Applying -> Unit
                    else -> Button(onClick = onSubmit, enabled = state.input.isNotBlank()) { Text(stringResource(R.string.assistant_send)) }
                }
            }
        }
    }
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

@Composable
private fun problemText(problem: AssistantProblem, projectEnd: String): String = when (problem) {
    AssistantProblem.SignedOut -> stringResource(R.string.assistant_signed_out)
    AssistantProblem.Offline -> stringResource(R.string.assistant_offline)
    AssistantProblem.Disabled -> stringResource(R.string.assistant_disabled)
    AssistantProblem.RateLimited -> stringResource(R.string.assistant_rate_limited)
    AssistantProblem.Unavailable -> stringResource(R.string.assistant_unavailable)
    AssistantProblem.NotUnderstood -> stringResource(R.string.assistant_not_understood)
    is AssistantProblem.Unsupported -> problem.reason ?: stringResource(R.string.assistant_unsupported)
    AssistantProblem.NothingToChange -> stringResource(R.string.assistant_nothing_to_change)
    AssistantProblem.ScheduleChanged -> stringResource(R.string.assistant_schedule_changed)
    is AssistantProblem.CannotDo -> {
        fun title(id: String) = problem.titles[id] ?: id
        when (val p = problem.problem) {
            is CommandProblem.UnknownTask -> stringResource(R.string.assistant_unknown_task)
            is CommandProblem.InvalidTask -> when (val rule = p.problem) {
                TaskProblem.BlankTitle -> stringResource(R.string.assistant_task_blank_title)
                TaskProblem.EndNotAfterStart -> stringResource(R.string.assistant_task_end_before_start, p.title)
                is TaskProblem.TooShort -> pluralStringResource(R.plurals.assistant_task_too_short, rule.minMinutes, p.title, rule.minMinutes)
                TaskProblem.OutsideProject -> stringResource(R.string.assistant_task_outside, p.title)
            }
            is CommandProblem.WouldCycle -> stringResource(R.string.assistant_would_cycle, title(p.predecessorId), title(p.successorId))
            is CommandProblem.AlreadyLinked -> stringResource(R.string.assistant_already_linked, title(p.predecessorId), title(p.successorId))
            is CommandProblem.NotLinked -> stringResource(R.string.assistant_not_linked, title(p.predecessorId), title(p.successorId))
            is CommandProblem.DoesNotFit -> stringResource(R.string.assistant_does_not_fit, projectEnd)
            is CommandProblem.Rejected -> stringResource(R.string.assistant_schedule_changed)
        }
    }
}
