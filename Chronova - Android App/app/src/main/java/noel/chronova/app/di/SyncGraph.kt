package noel.chronova.app.di

import noel.chronova.app.AppContainer
import noel.chronova.feature.sync.domain.DismissSyncPrompt
import noel.chronova.feature.sync.domain.ObserveSyncOverview
import noel.chronova.feature.sync.domain.ObserveSyncPrompt
import noel.chronova.feature.sync.domain.SyncNow
import noel.chronova.feature.sync.domain.SyncUseCases

/** The app-wide sync UI's wiring. */
class SyncGraph(private val app: AppContainer) {
    fun useCases(): SyncUseCases = SyncUseCases(
        observeOverview = ObserveSyncOverview(app.authRepository, app.syncCoordinator),
        observePrompt = ObserveSyncPrompt(app.syncCoordinator),
        syncNow = SyncNow(app.syncCoordinator),
        dismissPrompt = DismissSyncPrompt(app.syncCoordinator),
    )
}
