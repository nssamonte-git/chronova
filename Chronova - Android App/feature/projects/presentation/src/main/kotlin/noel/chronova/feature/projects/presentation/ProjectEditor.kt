package noel.chronova.feature.projects.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import noel.chronova.core.ui.TimeFormat
import noel.chronova.core.ui.R as CoreUiR

/** Bottom sheet to create or edit a project's name and date range. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorSheet(
    editor: ProjectEditorState,
    onNameChange: (String) -> Unit,
    onRangeChange: (LocalDate, LocalDate) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
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
                text = stringResource(if (editor.isNew) R.string.project_new else R.string.project_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.project_name)) },
                singleLine = true,
                isError = editor.problem == ProjectProblemUi.BlankName,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    // Otherwise the name field keeps focus and the keyboard covers the picker.
                    focusManager.clearFocus()
                    pickingDates = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Text(
                    text = TimeFormat.dateRange(editor.startDate, editor.endDate) + "  ·  " + pluralStringResource(
                        R.plurals.day_count,
                        dayCount(editor.startDate, editor.endDate),
                        dayCount(editor.startDate, editor.endDate),
                    ),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            editor.problem?.let { problem ->
                Text(
                    text = problemText(problem),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) }
                Button(onClick = onSave, enabled = !editor.saving, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(if (editor.isNew) R.string.create else CoreUiR.string.save))
                }
            }
        }
    }
    if (pickingDates) {
        DateRangeDialog(
            start = editor.startDate,
            end = editor.endDate,
            onConfirm = { start, end ->
                pickingDates = false
                onRangeChange(start, end)
            },
            onDismiss = { pickingDates = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    start: LocalDate,
    end: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The Material picker speaks UTC-midnight millis; LocalDate conversion is exact in UTC.
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start.toPickerMillis(),
        initialSelectedEndDateMillis = end.toPickerMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val startMillis = state.selectedStartDateMillis
            // A single tapped day is a one-day project.
            val endMillis = state.selectedEndDateMillis ?: startMillis
            TextButton(
                enabled = startMillis != null,
                onClick = { if (startMillis != null && endMillis != null) onConfirm(startMillis.toPickerDate(), endMillis.toPickerDate()) },
            ) { Text(stringResource(CoreUiR.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun problemText(problem: ProjectProblemUi): String = when (problem) {
    ProjectProblemUi.BlankName -> stringResource(R.string.problem_blank_name)
    ProjectProblemUi.EndBeforeStart -> stringResource(R.string.problem_end_before_start)
    is ProjectProblemUi.TooLong -> pluralStringResource(R.plurals.problem_too_long, problem.maxDays, problem.maxDays)
    is ProjectProblemUi.ExcludesTasks -> pluralStringResource(R.plurals.problem_excludes_tasks, problem.count, problem.count)
}

private fun dayCount(start: LocalDate, end: LocalDate): Int = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
