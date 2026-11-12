package noel.chronova.feature.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import noel.chronova.feature.sync.domain.SyncUseCases
import noel.chronova.core.ui.sync.SyncUi
import noel.chronova.feature.sync.domain.SyncPromptQueries

/** App-wide (activity-scoped) sync state for the project list and the reconnect prompt. */
class SyncViewModel(prompts: SyncPromptQueries, private val useCases: SyncUseCases) : ViewModel() {

    val ui: StateFlow<SyncUi> = useCases.observeOverview()
        .map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUi())

    val prompt: StateFlow<SyncPromptUi?> = prompts.prompt.map { it?.toUi() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, prompts.prompt.value?.toUi())

    fun syncNow() = useCases.syncNow()

    fun dismissPrompt() = useCases.dismissPrompt()

    companion object {
        fun factory(prompts: SyncPromptQueries, useCases: () -> SyncUseCases): ViewModelProvider.Factory = viewModelFactory {
            initializer { SyncViewModel(prompts, useCases()) }
        }
    }
}
