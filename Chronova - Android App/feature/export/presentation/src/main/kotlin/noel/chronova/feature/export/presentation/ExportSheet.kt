package noel.chronova.feature.export.presentation

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import noel.chronova.core.designsystem.ChronovaSpacing
import noel.chronova.core.ui.R as CoreUiR
import noel.chronova.core.ui.components.BannerKind
import noel.chronova.core.ui.components.ChronovaSheet
import noel.chronova.core.ui.components.InlineBanner
import noel.chronova.core.ui.components.SheetTitle
import noel.chronova.feature.export.domain.ExportFormat

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
    ChronovaSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.export_title, state.projectName))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ChronovaSpacing.s)) {
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
            InlineBanner(
                stringResource(
                    when (problem) {
                        ExportProblemUi.ProjectGone -> R.string.export_project_gone
                        ExportProblemUi.WriteFailed -> R.string.export_failed
                    },
                ),
                kind = BannerKind.Error,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) }
            Button(onClick = onExport, enabled = !state.working, modifier = Modifier.padding(start = ChronovaSpacing.s)) {
                if (state.working) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.export_action))
                }
            }
        }
    }
}

@Composable
private fun FormatRow(format: ExportFormat, selected: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Each choice is a card; the chosen one is outlined in the accent colour and tinted.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.primaryContainer.copy(alpha = 0.45f) else colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = ChronovaSpacing.m, vertical = ChronovaSpacing.m),
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
