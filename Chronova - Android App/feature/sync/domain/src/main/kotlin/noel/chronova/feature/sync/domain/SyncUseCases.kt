package noel.chronova.feature.sync.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import noel.chronova.core.domain.auth.AuthQueries
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.core.domain.sync.AccountSync
import noel.chronova.core.domain.sync.SyncStatusQueries
import noel.chronova.core.domain.sync.SyncPrompt
import noel.chronova.core.domain.sync.SyncStatus

/** App-wide sync, as the sync data layer runs it: the account's sync plus the reconnect prompt. */
/** Read-only view of the reconnect prompt, for the sync ViewModel. */
interface SyncPromptQueries {
    /** "You're back online with unsynced changes — sync now?", or null. */
    val prompt: StateFlow<SyncPrompt?>
}

interface SyncService : AccountSync, SyncPromptQueries {
    fun dismissPrompt()
}

/** What the project list shows about sync. */
data class SyncOverview(
    /** False in builds without a backend: no sync UI at all. */
    val configured: Boolean,
    val signedIn: Boolean,
    val pending: Int,
    val status: SyncStatus,
)

class ObserveSyncOverview(private val auth: AuthQueries, private val sync: SyncStatusQueries) {
    operator fun invoke(): Flow<SyncOverview> = combine(auth.state, sync.pendingCount, sync.status) { state, pending, status ->
        SyncOverview(
            configured = state != AuthState.NotConfigured,
            signedIn = state is AuthState.SignedIn,
            pending = pending,
            status = status,
        )
    }
}

/** Runs a sync now. Sync never starts on its own: only this (button, prompt, account page) starts it. */
class SyncNow(private val sync: SyncService) {
    operator fun invoke() = sync.syncNow()
}

class DismissSyncPrompt(private val sync: SyncService) {
    operator fun invoke() = sync.dismissPrompt()
}

/** Everything the app-wide sync UI does. */
class SyncUseCases(
    val observeOverview: ObserveSyncOverview,
    val syncNow: SyncNow,
    val dismissPrompt: DismissSyncPrompt,
)
