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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
import noel.chronova.core.designsystem.numeric
import noel.chronova.core.designsystem.toColor
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TimelineWindow
import noel.chronova.core.ui.R as CoreUiR
import noel.chronova.core.ui.TimeFormat
import noel.chronova.core.ui.components.BannerKind
import noel.chronova.core.ui.components.ChronovaDatePickerDialog
import noel.chronova.core.ui.components.ChronovaSheet
import noel.chronova.core.ui.components.InlineBanner
import noel.chronova.core.ui.components.SectionHeader
import noel.chronova.core.ui.components.SheetActions
import noel.chronova.core.ui.components.SheetTitle

private enum class Picking { START_DATE, START_TIME, END_DATE, END_TIME }

/** Bottom sheet to add or edit a task: title, colour, milestone or not, times, progress, notes and dependencies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    editor: TaskEditorState,
    otherTasks: List<TaskUi>,
    window: TimelineWindow,
    zone: ZoneId,
    onTogglePredecessor: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onColorChange: (TaskColor) -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onMilestoneChange: (Boolean) -> Unit,
    onProgressChange: (Int) -> Unit,
    onDoneChange: (Boolean) -> Unit,
    onNotesChange: (String) -> Unit,
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

    ChronovaSheet(onDismiss = onDismiss) {
        // The fields scroll; the problem and the buttons stay in view below them.
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(stringResource(if (editor.isNew) R.string.task_new else R.string.task_edit))
            OutlinedTextField(
                value = editor.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.task_title)) },
                singleLine = true,
                isError = editor.problem == TaskProblemUi.BlankTitle,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            ColorChoice(selected = editor.color, onSelect = onColorChange)
            SwitchRow(
                label = stringResource(R.string.task_milestone),
                checked = editor.isMilestone,
                onCheckedChange = onMilestoneChange,
            )
            DateTimeRow(
                label = stringResource(if (editor.isMilestone) R.string.task_when else R.string.task_starts),
                millis = editor.startMillis,
                zone = zone,
                onPickDate = { pick(Picking.START_DATE) },
                onPickTime = { pick(Picking.START_TIME) },
            )
            if (!editor.isMilestone) {
                DateTimeRow(
                    label = stringResource(R.string.task_ends),
                    millis = editor.endMillis,
                    zone = zone,
                    onPickDate = { pick(Picking.END_DATE) },
                    onPickTime = { pick(Picking.END_TIME) },
                )
            }
            editor.slack?.let { SlackLine(it, zone) }
            if (!editor.isMilestone) {
                ProgressChoice(progress = editor.progress, onChange = onProgressChange)
            }
            CheckboxRow(label = stringResource(R.string.task_done), checked = editor.isDone, onCheckedChange = onDoneChange)
            OutlinedTextField(
                value = editor.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.task_notes)) },
                minLines = 2,
                maxLines = 6,
                isError = editor.problem is TaskProblemUi.NotesTooLong,
                supportingText = {
                    Text(
                        stringResource(R.string.task_notes_count, editor.notes.length, NOTES_MAX_LENGTH),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (otherTasks.isNotEmpty()) {
                PredecessorChoice(
                    tasks = otherTasks,
                    selected = editor.predecessorIds,
                    forbidden = editor.forbiddenPredecessorIds,
                    onToggle = onTogglePredecessor,
                )
            }
        }
        editor.problem?.let { problem -> InlineBanner(problemText(problem), kind = BannerKind.Error) }
        SheetActions(
            primaryLabel = stringResource(if (editor.isNew) R.string.add else CoreUiR.string.save),
            onPrimary = onSave,
            onCancel = onDismiss,
            primaryEnabled = !editor.saving,
            destructiveLabel = if (editor.isNew) null else stringResource(CoreUiR.string.delete),
            onDestructive = onDelete,
        )
    }

    when (picking) {
        Picking.START_DATE -> DateDialog(editor.startMillis, window, zone, { onStartChange(it); picking = null }) { picking = null }
        Picking.END_DATE -> DateDialog(editor.endMillis, window, zone, { onEndChange(it); picking = null }) { picking = null }
        Picking.START_TIME -> TimeDialog(editor.startMillis, zone, { onStartChange(it); picking = null }) { picking = null }
        Picking.END_TIME -> TimeDialog(editor.endMillis, zone, { onEndChange(it); picking = null }) { picking = null }
        null -> Unit
    }
}

/** A labelled switch; the whole row toggles it. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A labelled checkbox; the whole row toggles it. */
@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Percent complete in steps of ten (a task set elsewhere, e.g. by voice, may sit between steps). */
@Composable
private fun ProgressChoice(progress: Int, onChange: (Int) -> Unit) {
    val label = stringResource(R.string.task_progress)
    val value = stringResource(R.string.task_progress_value, progress)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge.numeric())
        }
        Slider(
            value = progress.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..100f,
            steps = 9,
            // Still snaps to tens, without the row of tick dots along the track.
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** How much the saved task can slip before the project's finish moves. */
@Composable
private fun SlackLine(slack: SlackUi, zone: ZoneId) {
    val text = when (slack) {
        is SlackUi.OnCriticalPath -> stringResource(
            R.string.task_slack_critical,
            TimeFormat.day(slack.finishMillis, zone) + ", " + TimeFormat.clock(slack.finishMillis, zone),
        )
        is SlackUi.CanSlip -> stringResource(R.string.task_slack_can_slip, durationText(slack.millis))
    }
    if (slack is SlackUi.OnCriticalPath) {
        InlineBanner(text, kind = BannerKind.Warning)
    } else {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "2 d 4 h", "3 h 30 min", "45 min": the two largest units, rounded down to the minute. */
@Composable
private fun durationText(millis: Long): String = DurationParts.of(millis).map { (unit, amount) ->
    when (unit) {
        DurationParts.Unit.DAYS -> stringResource(R.string.duration_days, amount)
        DurationParts.Unit.HOURS -> stringResource(R.string.duration_hours, amount)
        DurationParts.Unit.MINUTES -> stringResource(R.string.duration_minutes, amount)
    }
}.joinToString(" ")

/** A length split into its two largest whole units (at least one, so 0 is "0 min"). */
internal object DurationParts {
    enum class Unit { DAYS, HOURS, MINUTES }

    fun of(millis: Long): List<Pair<Unit, Int>> {
        val minutes = (millis.coerceAtLeast(0L) / 60_000L)
        val parts = listOf(
            Unit.DAYS to (minutes / (24 * 60)).toInt(),
            Unit.HOURS to (minutes / 60 % 24).toInt(),
            Unit.MINUTES to (minutes % 60).toInt(),
        )
        val first = parts.indexOfFirst { it.second > 0 }
        if (first < 0) return listOf(Unit.MINUTES to 0)
        return parts.drop(first).take(2).filter { it.second > 0 }
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
            Text(TimeFormat.day(millis, zone), style = MaterialTheme.typography.labelLarge.numeric())
        }
        OutlinedButton(onClick = onPickTime) {
            Text(TimeFormat.clock(millis, zone), style = MaterialTheme.typography.labelLarge.numeric())
        }
    }
}

/** Tasks this one depends on. Picking a task that already depends on this one is disabled (cycle). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PredecessorChoice(
    tasks: List<TaskUi>,
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

/** The task's colour: four swatches; the chosen one has a ring and a check. */
@Composable
private fun ColorChoice(selected: TaskColor, onSelect: (TaskColor) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.task_color))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TaskColor.entries.forEach { color ->
                val isSelected = color == selected
                val name = colorName(color)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .then(if (isSelected) Modifier.border(2.dp, color.toColor(), CircleShape) else Modifier)
                        .clickable(role = Role.RadioButton) { onSelect(color) }
                        .semantics {
                            contentDescription = name
                            this.selected = isSelected
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(32.dp).background(color.toColor(), CircleShape), contentAlignment = Alignment.Center) {
                        if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun colorName(color: TaskColor): String = stringResource(
    when (color) {
        TaskColor.BLUE -> R.string.color_blue
        TaskColor.GREEN -> R.string.color_green
        TaskColor.PURPLE -> R.string.color_purple
        TaskColor.AMBER -> R.string.color_amber
    },
)

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
    ChronovaDatePickerDialog(
        initial = current.toLocalDate(),
        onConfirm = { date -> onConfirm(date.atTime(current.toLocalTime()).atZone(zone).toInstant().toEpochMilli()) },
        onDismiss = onDismiss,
        selectable = firstDay..lastDay,
    )
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
private fun problemText(problem: TaskProblemUi): String = when (problem) {
    TaskProblemUi.BlankTitle -> stringResource(R.string.task_problem_blank_title)
    TaskProblemUi.EndNotAfterStart -> stringResource(R.string.task_problem_end_not_after_start)
    is TaskProblemUi.TooShort -> pluralStringResource(R.plurals.task_problem_too_short, problem.minMinutes, problem.minMinutes)
    TaskProblemUi.OutsideProject -> stringResource(R.string.task_problem_outside_project)
    is TaskProblemUi.NotesTooLong -> pluralStringResource(R.plurals.task_problem_notes_too_long, problem.maxLength, problem.maxLength)
    TaskProblemUi.BadProgress -> stringResource(R.string.task_problem_bad_progress)
}

