package noel.chronova.feature.projects.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import noel.chronova.core.ui.R as CoreUiR
import noel.chronova.core.ui.TimeFormat
import noel.chronova.core.ui.components.BannerKind
import noel.chronova.core.ui.components.ChronovaDatePickerDialog
import noel.chronova.core.ui.components.ChronovaSheet
import noel.chronova.core.ui.components.InlineBanner
import noel.chronova.core.ui.components.SheetActions
import noel.chronova.core.ui.components.SheetTitle
import noel.chronova.core.ui.components.toPickerDate
import noel.chronova.core.ui.components.toPickerMillis

/**
 * Bottom sheet to create or edit a project's name and date range. A new project can
 * start from an existing one (a copy keeps its length, so only the start is picked).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorSheet(
    editor: ProjectEditorState,
    onNameChange: (String) -> Unit,
    onRangeChange: (LocalDate, LocalDate) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onStartFrom: (String?) -> Unit = {},
    /** Offers "An image…" under Start from (a new project read from a chart); null hides it. */
    onFromImage: (() -> Unit)? = null,
) {
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    ChronovaSheet(onDismiss = onDismiss) {
        SheetTitle(
            when {
                editor.copyFrom != null -> stringResource(R.string.project_copy_title, editor.copyFrom.name)
                editor.isNew -> stringResource(R.string.project_new)
                else -> stringResource(R.string.project_edit)
            },
        )
        if (editor.isNew && (editor.sources.isNotEmpty() || onFromImage != null)) {
            StartFromChoice(editor.copyFrom, editor.sources, onStartFrom, onFromImage)
        }
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
        if (editor.isCopy) {
            Text(
                text = stringResource(R.string.project_copy_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        editor.problem?.let { problem -> InlineBanner(problemText(problem), kind = BannerKind.Error) }
        SheetActions(
            primaryLabel = stringResource(if (editor.isNew) R.string.create else CoreUiR.string.save),
            onPrimary = onSave,
            onCancel = onDismiss,
            primaryEnabled = !editor.saving,
        )
    }
    if (pickingDates && editor.isCopy) {
        ChronovaDatePickerDialog(
            initial = editor.startDate,
            onConfirm = { start ->
                pickingDates = false
                onRangeChange(start, start)
            },
            onDismiss = { pickingDates = false },
        )
    } else if (pickingDates) {
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

/** "Start from: Blank ▾" and a menu of the projects a new one can copy. */
@Composable
private fun StartFromChoice(selected: ProjectSourceUi?, sources: List<ProjectSourceUi>, onStartFrom: (String?) -> Unit, onFromImage: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    val blank = stringResource(R.string.project_start_blank)
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.project_start_from, selected?.name ?: blank),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(blank) }, onClick = { open = false; onStartFrom(null) })
            if (onFromImage != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.project_start_image)) }, onClick = { open = false; onFromImage() })
            }
            sources.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { open = false; onStartFrom(source.id) },
                )
            }
        }
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
    ProjectProblemUi.SourceGone -> stringResource(R.string.problem_source_gone)
}

private fun dayCount(start: LocalDate, end: LocalDate): Int = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

