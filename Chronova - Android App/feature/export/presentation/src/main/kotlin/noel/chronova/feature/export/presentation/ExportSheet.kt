package noel.chronova.feature.export.presentation

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import noel.chronova.feature.export.domain.ExportFormat
import noel.chronova.core.ui.R as CoreUiR

/** The export sheet, and Android's share sheet once the file is written. */
@Composable
fun ExportScreen(viewModel: ExportViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val share by viewModel.share.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.export_share_title)
    LaunchedEffect(share) {
        val file = share ?: return@LaunchedEffect
        val uri = file.uri.toUri()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            // The receiving app may read this one file, nothing else.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
        viewModel.onShared()
        onDismiss()
    }
    ExportSheet(
        state = state,
        onFormat = viewModel::chooseFormat,
        onIncludeNotes = viewModel::setIncludeNotes,
        onExport = viewModel::export,
        onDismiss = onDismiss,
    )
}

/** Pick PDF, CSV or calendar, and whether notes go in; Export writes the file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    state: ExportSheetState,
    onFormat: (ExportFormat) -> Unit,
    onIncludeNotes: (Boolean) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.export_title, state.projectName), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.selectableGroup()) {
                ExportFormat.entries.forEach { format ->
                    FormatRow(format, selected = format == state.format, onSelect = { onFormat(format) })
                }
            }
            if (state.format != ExportFormat.PDF) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = state.includeNotes, role = Role.Switch, onValueChange = onIncludeNotes),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.export_include_notes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = state.includeNotes, onCheckedChange = null)
                }
            }
            state.problem?.let { problem ->
                Text(
                    stringResource(
                        when (problem) {
                            ExportProblemUi.ProjectGone -> R.string.export_project_gone
                            ExportProblemUi.WriteFailed -> R.string.export_failed
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) }
                Button(onClick = onExport, enabled = !state.working, modifier = Modifier.padding(start = 8.dp)) {
                    if (state.working) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.export_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatRow(format: ExportFormat, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                stringResource(
                    when (format) {
                        ExportFormat.PDF -> R.string.export_pdf
                        ExportFormat.CSV -> R.string.export_csv
                        ExportFormat.CALENDAR -> R.string.export_calendar
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(
                    when (format) {
                        ExportFormat.PDF -> R.string.export_pdf_body
                        ExportFormat.CSV -> R.string.export_csv_body
                        ExportFormat.CALENDAR -> R.string.export_calendar_body
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
