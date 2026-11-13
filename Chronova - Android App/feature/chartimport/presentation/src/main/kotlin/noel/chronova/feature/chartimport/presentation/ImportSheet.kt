package noel.chronova.feature.chartimport.presentation

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import noel.chronova.core.ui.TimeFormat
import noel.chronova.core.ui.R as CoreUiR

/** What the sheet's buttons do. */
data class ImportActions(
    val takePhoto: () -> Unit = {},
    val chooseImage: () -> Unit = {},
    val cancelReading: () -> Unit = {},
    val tryAnother: () -> Unit = {},
    val toggle: (String) -> Unit = {},
    val setStart: (LocalDate) -> Unit = {},
    val setName: (String) -> Unit = {},
    val setProjectDays: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    val apply: () -> Unit = {},
    val undo: () -> Unit = {},
    val openProject: (String) -> Unit = {},
    val close: () -> Unit = {},
)

/**
 * The import sheet with the camera and the photo picker. Neither needs a permission: the camera
 * app writes into a file it is lent for the one photo, and the system picker lends the one image.
 */
@Composable
fun ImportScreen(viewModel: ImportViewModel, onDismiss: () -> Unit, onOpenProject: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), viewModel::onPhotoTaken)
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> viewModel.onImageChosen(uri?.toString()) }
    LaunchedEffect(camera) {
        val uri = camera ?: return@LaunchedEffect
        val opened = try {
            takePicture.launch(uri.toUri())
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
        viewModel.onCameraOpened(opened)
    }
    ImportSheet(
        state = state,
        actions = ImportActions(
            takePhoto = viewModel::takePhoto,
            chooseImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            cancelReading = viewModel::cancelReading,
            tryAnother = viewModel::backToChoose,
            toggle = viewModel::toggle,
            setStart = viewModel::setStart,
            setName = viewModel::setName,
            setProjectDays = viewModel::setProjectDays,
            apply = viewModel::apply,
            undo = viewModel::undo,
            openProject = { id ->
                onDismiss()
                onOpenProject(id)
            },
            close = onDismiss,
        ),
        onDismiss = {
            viewModel.cancelReading()
            onDismiss()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(state: ImportSheetUi, actions: ImportActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ImportSheetContent(state, actions)
    }
}

/** The sheet's content (screenshots render this alone). */
@Composable
fun ImportSheetContent(state: ImportSheetUi, actions: ImportActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            is ImportSheetUi.Choose -> Choose(state, actions)
            ImportSheetUi.Reading -> Reading(actions)
            is ImportSheetUi.Preview -> Preview(state.preview, actions)
            is ImportSheetUi.Problem -> Problem(state.problem, actions)
            is ImportSheetUi.Done -> Done(state.done, actions)
        }
    }
}

@Composable
private fun Title(newProject: Boolean, projectName: String) {
    Text(
        stringResource(if (newProject) R.string.import_title_new else R.string.import_title_add),
        style = MaterialTheme.typography.titleLarge,
    )
    if (!newProject && projectName.isNotEmpty()) {
        Text(
            stringResource(R.string.import_into, projectName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Choose(state: ImportSheetUi.Choose, actions: ImportActions) {
    Title(state.newProject, state.projectName)
    Text(stringResource(R.string.import_choose_body), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = actions.takePhoto, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.import_take_photo)) }
        OutlinedButton(onClick = actions.chooseImage, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.import_choose_image)) }
    }
    Text(
        stringResource(R.string.import_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Reading(actions: ImportActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(Modifier.size(32.dp))
        Column {
            Text(stringResource(R.string.import_reading), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.import_reading_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = actions.cancelReading) { Text(stringResource(CoreUiR.string.cancel)) }
    }
}

@Composable
private fun Preview(preview: ImportPreviewUi, actions: ImportActions) {
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var pickingDays by rememberSaveable { mutableStateOf(false) }

    Title(preview.newProject, preview.projectName)
    Text(found(preview), style = MaterialTheme.typography.titleSmall)
    if (preview.newProject) {
        OutlinedTextField(
            value = preview.projectName,
            onValueChange = actions.setName,
            label = { Text(stringResource(R.string.import_project_name)) },
            singleLine = true,
            isError = preview.problem == ImportPlanProblemUi.BlankName,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (preview.relative) {
        LabelledChange(stringResource(R.string.import_starts, TimeFormat.dateRange(preview.start, preview.start))) { pickingStart = true }
    } else if (preview.newProject) {
        LabelledChange(stringResource(R.string.import_project_days, TimeFormat.dateRange(preview.projectStart, preview.projectEnd))) { pickingDays = true }
    }
    if (preview.uncertain.isNotEmpty()) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.import_check_these), style = MaterialTheme.typography.labelLarge)
                preview.uncertain.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
        items(preview.rows, key = { it.ref }) { row -> TaskRow(row, onToggle = { actions.toggle(row.ref) }) }
    }
    preview.problem?.let { problem ->
        Text(planProblem(problem), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    Button(
        onClick = actions.apply,
        enabled = !preview.applying && preview.selectedCount > 0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            pluralStringResource(
                if (preview.newProject) R.plurals.import_apply_new else R.plurals.import_apply_add,
                preview.selectedCount,
                preview.selectedCount,
            ),
        )
    }

    if (pickingStart) {
        DayDialog(preview.start, onConfirm = { pickingStart = false; actions.setStart(it) }, onDismiss = { pickingStart = false })
    }
    if (pickingDays) {
        DaysDialog(
            preview.projectStart,
            preview.projectEnd,
            onConfirm = { start, end -> pickingDays = false; actions.setProjectDays(start, end) },
            onDismiss = { pickingDays = false },
        )
    }
}

@Composable
private fun found(preview: ImportPreviewUi): String = buildList {
    add(pluralStringResource(R.plurals.import_found_tasks, preview.rows.size, preview.rows.size))
    if (preview.milestoneCount > 0) add(pluralStringResource(R.plurals.import_found_milestones, preview.milestoneCount, preview.milestoneCount))
    if (preview.linkCount > 0) add(pluralStringResource(R.plurals.import_found_links, preview.linkCount, preview.linkCount))
}.joinToString(" · ")

@Composable
private fun LabelledChange(label: String, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onChange) { Text(stringResource(R.string.import_change)) }
    }
}

@Composable
private fun TaskRow(row: ImportRowUi, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = row.selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = row.selected, onCheckedChange = null)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val details = buildList {
                add(if (row.milestone) stringResource(R.string.import_milestone) + " · " + TimeFormat.dateRange(row.firstDay, row.firstDay) else TimeFormat.dateRange(row.firstDay, row.lastDay))
                if (row.progress > 0) add(stringResource(R.string.import_progress, row.progress))
            }.joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!row.fits) {
                Text(stringResource(R.string.import_outside), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Problem(problem: ImportProblemUi, actions: ImportActions) {
    Text(
        stringResource(
            when (problem) {
                ImportProblemUi.Offline -> R.string.import_problem_offline
                ImportProblemUi.NotASchedule -> R.string.import_problem_not_schedule
                ImportProblemUi.Unreadable -> R.string.import_problem_unreadable
                ImportProblemUi.BadImage -> R.string.import_problem_bad_image
                ImportProblemUi.NoCamera -> R.string.import_problem_no_camera
                ImportProblemUi.TooMany -> R.string.import_problem_too_many
                ImportProblemUi.SwitchedOff -> R.string.import_problem_switched_off
                ImportProblemUi.SignInAgain -> R.string.import_problem_sign_in
                ImportProblemUi.ProjectGone -> R.string.import_problem_project_gone
                ImportProblemUi.Unavailable -> R.string.import_problem_unavailable
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = actions.close) { Text(stringResource(R.string.import_close)) }
        if (problem != ImportProblemUi.ProjectGone && problem != ImportProblemUi.SwitchedOff && problem != ImportProblemUi.SignInAgain) {
            Button(onClick = actions.tryAnother) { Text(stringResource(R.string.import_try_another)) }
        }
    }
}

@Composable
private fun Done(done: ImportDoneUi, actions: ImportActions) {
    Text(
        pluralStringResource(R.plurals.import_done_body, done.taskCount, done.taskCount, done.projectName),
        style = MaterialTheme.typography.bodyLarge,
    )
    when (done.undone) {
        true -> Text(stringResource(R.string.import_undone), style = MaterialTheme.typography.bodyMedium)
        false -> Text(stringResource(R.string.import_undo_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        null -> Unit
    }
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        if (done.undone == null) TextButton(onClick = actions.undo) { Text(stringResource(R.string.import_undo)) }
        if (done.newProject && done.undone != true) {
            Button(onClick = { actions.openProject(done.projectId) }) { Text(stringResource(R.string.import_open_project)) }
        } else {
            Button(onClick = actions.close) { Text(stringResource(R.string.import_close)) }
        }
    }
}

@Composable
private fun planProblem(problem: ImportPlanProblemUi): String = when (problem) {
    ImportPlanProblemUi.NothingSelected -> stringResource(R.string.import_plan_nothing)
    ImportPlanProblemUi.BlankName -> stringResource(R.string.import_plan_blank_name)
    ImportPlanProblemUi.EndBeforeStart -> stringResource(R.string.import_plan_end_before_start)
    is ImportPlanProblemUi.TooLong -> pluralStringResource(R.plurals.import_plan_too_long, problem.maxDays, problem.maxDays)
    is ImportPlanProblemUi.DoesNotFit -> stringResource(R.string.import_plan_does_not_fit, problem.title)
    ImportPlanProblemUi.ProjectChanged -> stringResource(R.string.import_plan_changed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDialog(day: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = day.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val millis = state.selectedDateMillis
            TextButton(enabled = millis != null, onClick = { if (millis != null) onConfirm(millis.toPickerDate()) }) { Text(stringResource(CoreUiR.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaysDialog(start: LocalDate, end: LocalDate, onConfirm: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDateRangePickerState(initialSelectedStartDateMillis = start.toPickerMillis(), initialSelectedEndDateMillis = end.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val first = state.selectedStartDateMillis
            val last = state.selectedEndDateMillis
            TextButton(enabled = first != null && last != null, onClick = { if (first != null && last != null) onConfirm(first.toPickerDate(), last.toPickerDate()) }) {
                Text(stringResource(CoreUiR.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) } },
    ) { DateRangePicker(state = state, modifier = Modifier.weight(1f)) }
}

/** The pickers work in UTC midnights. */
private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
