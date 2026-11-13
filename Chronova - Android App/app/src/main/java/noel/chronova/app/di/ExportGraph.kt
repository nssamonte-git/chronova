package noel.chronova.app.di

import android.content.Context
import java.time.ZoneId
import noel.chronova.app.AppContainer
import noel.chronova.feature.export.data.CacheExportFiles
import noel.chronova.feature.export.data.GanttPdfWriter
import noel.chronova.feature.export.domain.ExportProject

/** The export sheet's wiring: the PDF writer and the shared cache folder. */
class ExportGraph(private val app: AppContainer, context: Context) {
    private val files = CacheExportFiles(context.applicationContext)

    /** The time zone and locale are read when the sheet opens. */
    fun exportProject(): ExportProject = ExportProject(app.projectRepository, GanttPdfWriter(), files, ZoneId.systemDefault())
}
