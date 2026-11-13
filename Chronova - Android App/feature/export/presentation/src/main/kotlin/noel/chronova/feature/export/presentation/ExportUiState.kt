package noel.chronova.feature.export.presentation

import noel.chronova.feature.export.domain.ExportFormat

/** The export sheet for one project. */
data class ExportSheetState(
    val projectName: String,
    val format: ExportFormat = ExportFormat.PDF,
    /** Notes go into the CSV and calendar files only while this is on (the PDF has none). */
    val includeNotes: Boolean = true,
    val working: Boolean = false,
    val problem: ExportProblemUi? = null,
)

enum class ExportProblemUi {
    /** The project was deleted meanwhile. */
    ProjectGone,

    /** Writing the file failed (e.g. no space left). */
    WriteFailed,
}

/** A written export, handed to Android's share sheet once. */
data class ShareFileUi(val name: String, val mimeType: String, val uri: String)
