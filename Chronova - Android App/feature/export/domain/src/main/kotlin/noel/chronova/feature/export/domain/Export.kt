package noel.chronova.feature.export.domain

import java.time.ZoneId
import noel.chronova.core.domain.repository.ProjectRepository
import noel.chronova.core.domain.schedule.ScheduleState

/** What a project can be exported as. */
enum class ExportFormat(val extension: String, val mimeType: String) {
    /** The whole project as a printable Gantt chart. */
    PDF("pdf", "application/pdf"),

    /** One row per task, for spreadsheets. */
    CSV("csv", "text/csv"),

    /** Every task as an event, for calendar apps. */
    CALENDAR("ics", "text/calendar"),
}

/** A written export, ready to share: [location] is where it can be read (a content URI on Android). */
data class ExportedFile(val name: String, val mimeType: String, val location: String)

/** Draws a project's Gantt chart as a PDF (Android's PdfDocument in the app). */
fun interface PdfChartWriter {
    fun write(schedule: ScheduleState, zone: ZoneId, exportedAtMillis: Long): ByteArray
}

/** Where exports are written before they are shared; each save replaces the last export. */
fun interface ExportFiles {
    suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): ExportedFile
}

sealed interface ExportResult {
    data class Ready(val file: ExportedFile) : ExportResult

    /** The project was deleted meanwhile (e.g. by a sync). */
    data object ProjectGone : ExportResult
}

/**
 * Exports a project as [ExportFormat]: reads its schedule as it is now, builds the file
 * (notes only when [includeNotes]; the PDF has no notes) and saves it for sharing.
 * Nothing leaves the phone here: the user picks where it goes in the share sheet.
 */
class ExportProject(
    private val repository: ProjectRepository,
    private val pdf: PdfChartWriter,
    private val files: ExportFiles,
    private val zone: ZoneId,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(projectId: String, format: ExportFormat, includeNotes: Boolean): ExportResult {
        val schedule = repository.schedule(projectId) ?: return ExportResult.ProjectGone
        val now = clock()
        val bytes = when (format) {
            ExportFormat.PDF -> pdf.write(schedule, zone, now)
            ExportFormat.CSV -> CsvExport.build(schedule, zone, includeNotes)
            ExportFormat.CALENDAR -> IcsExport.build(schedule, includeNotes, now)
        }
        return ExportResult.Ready(files.save(fileName(schedule.project.name, format), format.mimeType, bytes))
    }

    companion object {
        /** The project's name made safe for a file name ("Office move.pdf"), or "project". */
        fun fileName(projectName: String, format: ExportFormat): String {
            val safe = projectName.map { if (it.isLetterOrDigit() || it in " -_") it else '-' }.joinToString("")
                .trim().take(MAX_NAME).trim().takeIf { name -> name.any { it.isLetterOrDigit() } } ?: "project"
            return "$safe.${format.extension}"
        }

        private const val MAX_NAME = 60
    }
}
