package noel.chronova.app.di

import noel.chronova.app.AppContainer
import noel.chronova.feature.sync.domain.DismissSyncPrompt
import noel.chronova.feature.sync.domain.ObserveSyncOverview
import noel.chronova.feature.sync.domain.SyncPromptQueries
import noel.chronova.feature.sync.domain.SyncNow
import noel.chronova.feature.sync.domain.SyncUseCases

/** The app-wide sync UI's wiring. */
class SyncGraph(private val app: AppContainer) {
    /** The reconnect prompt, read by the sync ViewModel. */
    val prompts: SyncPromptQueries get() = app.syncCoordinator

    fun useCases(): SyncUseCases = SyncUseCases(
        observeOverview = ObserveSyncOverview(app.authRepository, app.syncCoordinator),
        syncNow = SyncNow(app.syncCoordinator),
        dismissPrompt = DismissSyncPrompt(app.syncCoordinator),
    )
}
