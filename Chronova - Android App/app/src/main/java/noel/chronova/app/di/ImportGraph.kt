package noel.chronova.app.di

import android.content.Context
import java.time.ZoneId
import noel.chronova.app.AppContainer
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.feature.chartimport.data.AndroidChartImages
import noel.chronova.feature.chartimport.domain.ApplyImport
import noel.chronova.feature.chartimport.domain.ImportUseCases
import noel.chronova.feature.chartimport.domain.LoadImportTarget
import noel.chronova.feature.chartimport.domain.PlanImport
import noel.chronova.feature.chartimport.domain.ReadChart
import noel.chronova.feature.chartimport.domain.UndoImport

/** The import sheet's wiring (schedules from images, docs/import-api.md). */
class ImportGraph(private val app: AppContainer, context: Context) {
    private val images = AndroidChartImages(context.applicationContext)

    /** The time zone is read when the sheet opens. */
    fun useCases(): ImportUseCases {
        val zone = ZoneId.systemDefault()
        val applyChanges = ApplyScheduleChanges(app.projectRepository, zone)
        return ImportUseCases(
            images = images,
            readChart = ReadChart(app.importApi, images, app.authRepository::accessToken, { zone }),
            loadTarget = LoadImportTarget(app.projectRepository),
            plan = PlanImport(zone),
            apply = ApplyImport(app.projectRepository, applyChanges, app.notificationRepository),
            undo = UndoImport(app.projectRepository, applyChanges),
        )
    }
}
