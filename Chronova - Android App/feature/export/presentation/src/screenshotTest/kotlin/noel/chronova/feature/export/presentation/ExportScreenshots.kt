package noel.chronova.feature.export.presentation

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.feature.export.domain.ExportFormat

@Composable
private fun Sheet(state: ExportSheetState) = ChronovaTheme {
    ExportSheet(state = state, onFormat = {}, onIncludeNotes = {}, onExport = {}, onDismiss = {})
}

/** The export sheet as it opens: PDF chosen. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ExportSheetPdf() = Sheet(ExportSheetState("Office move"))

/** The calendar export with the notes switch off. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ExportSheetCalendarWithoutNotes() = Sheet(ExportSheetState("Office move", format = ExportFormat.CALENDAR, includeNotes = false))
