package noel.chronova.app.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import noel.chronova.app.auth.AuthRepository
import noel.chronova.app.auth.AuthState
import noel.chronova.app.data.sync.SyncCoordinator
import noel.chronova.app.data.sync.SyncPrompt
import noel.chronova.app.data.sync.SyncStatus

/** What the project list shows about sync. */
data class SyncUi(
    /** False in builds without a backend: no sync UI at all. */
    val configured: Boolean = false,
    val signedIn: Boolean = false,
    val pending: Int = 0,
    val status: SyncStatus = SyncStatus.Idle,
)

/** App-wide (activity-scoped) sync state for the project list and the reconnect prompt. */
class SyncViewModel(private val coordinator: SyncCoordinator, auth: AuthRepository) : ViewModel() {

    val ui: StateFlow<SyncUi> = combine(auth.state, coordinator.pendingCount, coordinator.status) { state, pending, status ->
        SyncUi(
            configured = state != AuthState.NotConfigured,
            signedIn = state is AuthState.SignedIn,
            pending = pending,
            status = status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUi())

    val prompt: StateFlow<SyncPrompt?> = coordinator.prompt

    fun syncNow() = coordinator.syncNow()

    fun dismissPrompt() = coordinator.dismissPrompt()

    companion object {
        fun factory(coordinator: SyncCoordinator, auth: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SyncViewModel(coordinator, auth) }
        }
    }
}
