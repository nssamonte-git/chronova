package noel.chronova.feature.timeline.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import noel.chronova.core.domain.TaskProblem
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TimelineWindow
import noel.chronova.core.ui.TimeFormat
import noel.chronova.core.designsystem.toColor
import noel.chronova.core.ui.R as CoreUiR

private enum class Picking { START_DATE, START_TIME, END_DATE, END_TIME }

/** Bottom sheet to add or edit a task: title, colour, start and end. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    editor: TaskEditorState,
    otherTasks: List<SchedulerTask>,
    window: TimelineWindow,
    zone: ZoneId,
    onTogglePredecessor: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onColorChange: (TaskColor) -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }
    val focusManager = LocalFocusManager.current
    fun pick(what: Picking) {
        focusManager.clearFocus() // keep the keyboard from covering the picker
        picking = what
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(if (editor.isNew) R.string.task_new else R.string.task_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = editor.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.task_title)) },
                singleLine = true,
                isError = editor.problem == TaskProblem.BlankTitle,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            ColorChoice(selected = editor.color, onSelect = onColorChange)
            DateTimeRow(
                label = stringResource(R.string.task_starts),
                millis = editor.startMillis,
                zone = zone,
                onPickDate = { pick(Picking.START_DATE) },
                onPickTime = { pick(Picking.START_TIME) },
            )
            DateTimeRow(
                label = stringResource(R.string.task_ends),
                millis = editor.endMillis,
                zone = zone,
                onPickDate = { pick(Picking.END_DATE) },
                onPickTime = { pick(Picking.END_TIME) },
            )
            if (otherTasks.isNotEmpty()) {
                PredecessorChoice(
                    tasks = otherTasks,
                    selected = editor.predecessorIds,
                    forbidden = editor.forbiddenPredecessorIds,
                    onToggle = onTogglePredecessor,
                )
            }
            editor.problem?.let { problem ->
                Text(
                    text = problemText(problem),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!editor.isNew) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(CoreUiR.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) }
                Button(onClick = onSave, enabled = !editor.saving, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(if (editor.isNew) R.string.add else CoreUiR.string.save))
                }
            }
        }
    }

    when (picking) {
        Picking.START_DATE -> DateDialog(editor.startMillis, window, zone, { onStartChange(it); picking = null }) { picking = null }
        Picking.END_DATE -> DateDialog(editor.endMillis, window, zone, { onEndChange(it); picking = null }) { picking = null }
        Picking.START_TIME -> TimeDialog(editor.startMillis, zone, { onStartChange(it); picking = null }) { picking = null }
        Picking.END_TIME -> TimeDialog(editor.endMillis, zone, { onEndChange(it); picking = null }) { picking = null }
        null -> Unit
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    millis: Long,
    zone: ZoneId,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
        OutlinedButton(onClick = onPickDate, modifier = Modifier.weight(1f)) {
            Text(TimeFormat.day(millis, zone))
        }
        OutlinedButton(onClick = onPickTime) {
            Text(TimeFormat.clock(millis, zone))
        }
    }
}

/** Tasks this one depends on. Picking a task that already depends on this one is disabled (cycle). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PredecessorChoice(
    tasks: List<SchedulerTask>,
    selected: Set<String>,
    forbidden: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.task_depends_on), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tasks.forEach { task ->
                FilterChip(
                    selected = task.id in selected,
                    onClick = { onToggle(task.id) },
                    enabled = task.id !in forbidden,
                    label = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(task.color.toColor(), CircleShape),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorChoice(selected: TaskColor, onSelect: (TaskColor) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskColor.entries.forEach { color ->
            val isSelected = color == selected
            val name = color.name.lowercase().replaceFirstChar { it.uppercase() }
            Box(
                Modifier
                    .size(36.dp)
                    .background(color.toColor(), CircleShape)
                    .then(
                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                    )
                    .clickable(role = Role.RadioButton) { onSelect(color) }
                    .semantics {
                        contentDescription = name
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}

/** Picks a day inside the project; the time of day of [millis] is kept. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    millis: Long,
    window: TimelineWindow,
    zone: ZoneId,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = Instant.ofEpochMilli(millis).atZone(zone)
    val firstDay = Instant.ofEpochMilli(window.startMillis).atZone(zone).toLocalDate()
    // A task may end exactly at the window end (midnight after the last day).
    val lastDay = Instant.ofEpochMilli(window.endMillis).atZone(zone).toLocalDate()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current.toLocalDate().toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis.toPickerDate() in firstDay..lastDay

            override fun isSelectableYear(year: Int): Boolean = year in firstDay.year..lastDay.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    val date = state.selectedDateMillis?.toPickerDate() ?: return@TextButton
                    onConfirm(date.atTime(current.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
                },
            ) { Text(stringResource(CoreUiR.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** Picks a time of day; the date of [millis] is kept. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    millis: Long,
    zone: ZoneId,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = Instant.ofEpochMilli(millis).atZone(zone)
    val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val time = LocalTime.of(state.hour, state.minute)
                onConfirm(current.toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli())
            }) { Text(stringResource(CoreUiR.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun problemText(problem: TaskProblem): String = when (problem) {
    TaskProblem.BlankTitle -> stringResource(R.string.task_problem_blank_title)
    TaskProblem.EndNotAfterStart -> stringResource(R.string.task_problem_end_not_after_start)
    is TaskProblem.TooShort -> pluralStringResource(R.plurals.task_problem_too_short, problem.minMinutes, problem.minMinutes)
    TaskProblem.OutsideProject -> stringResource(R.string.task_problem_outside_project)
}

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
