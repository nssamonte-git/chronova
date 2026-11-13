package noel.chronova.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import noel.chronova.core.designsystem.ChronovaSpacing
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.ui.R

/**
 * The app's card: the surface colour with a hairline border instead of a shadow, so it reads
 * the same on the light page and on navy. Clickable when [onClick] is given.
 */
@Composable
fun ChronovaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val border = BorderStroke(1.dp, colors.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, color = colors.surface, border = border, content = content)
    } else {
        Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = colors.surface, border = border, content = content)
    }
}

/**
 * A bottom sheet that opens fully, with the app's gutters. [content] is laid out by
 * [SheetColumn], which previews and screenshot tests can use on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronovaSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        SheetColumn(content = content)
    }
}

/** A sheet's content column: side and bottom gutters, clear of the navigation bar and the keyboard. */
@Composable
fun SheetColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ChronovaSpacing.sheetGutter)
            .padding(bottom = ChronovaSpacing.sheetGutter)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(ChronovaSpacing.l),
        content = content,
    )
}

/** A sheet's heading. */
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}

/**
 * The buttons at the bottom of a sheet: Cancel and the main action on the right, and a
 * destructive action (Delete) on the left when there is one.
 */
@Composable
fun SheetActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    destructiveLabel: String? = null,
    onDestructive: () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (destructiveLabel != null) DestructiveTextButton(destructiveLabel, onDestructive)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.padding(start = ChronovaSpacing.s)) {
            Text(primaryLabel)
        }
    }
}

/** A text button for something that deletes or signs out: the error colour. */
@Composable
fun DestructiveTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(label, color = if (enabled) MaterialTheme.colorScheme.error else Color.Unspecified)
    }
}

/** A yes/no question. A [destructive] confirm button is in the error colour. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            if (destructive) {
                DestructiveTextButton(confirmLabel, onConfirm)
            } else {
                TextButton(onClick = onConfirm) { Text(confirmLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

/**
 * Picks one day. [selectable] limits the days that can be picked (a task's day must be inside
 * its project). The Material picker speaks UTC-midnight millis; the conversion to [LocalDate]
 * is exact in UTC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronovaDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    selectable: ClosedRange<LocalDate>? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toPickerMillis(),
        selectableDates = if (selectable == null) {
            AllDates
        } else {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toPickerDate() in selectable

                override fun isSelectableYear(year: Int): Boolean = year in selectable.start.year..selectable.endInclusive.year
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val millis = state.selectedDateMillis
            TextButton(enabled = millis != null, onClick = { if (millis != null) onConfirm(millis.toPickerDate()) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private object AllDates : SelectableDates

/** A day as the Material date pickers take it: UTC midnight, in millis. */
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The day a Material date picker returned. */
fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * The screen's main action, floating bottom-right ("New project", "Add task"): the logo's blue
 * to violet, with a white label, the same in both themes. The label is also its description,
 * so a screen reader reads it once.
 */
@Composable
fun AuroraFab(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(ChronovaTheme.extended.auroraInkBrush)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            icon()
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The assistant's orb (when there is one) above the screen's main floating button. */
@Composable
fun FabColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ChronovaSpacing.m), content = content)
}

/** The room a page leaves under its last item, so it scrolls clear of the floating buttons. */
object FabClearance {
    /** Below a list with one floating button. */
    val single = 96.dp

    /** Below a list with the orb above the button. */
    val withOrb = 172.dp
}
