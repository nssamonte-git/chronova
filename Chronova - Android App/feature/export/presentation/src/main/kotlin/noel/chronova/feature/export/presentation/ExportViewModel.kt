package noel.chronova.feature.export.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import noel.chronova.feature.export.domain.ExportFormat
import noel.chronova.feature.export.domain.ExportProject
import noel.chronova.feature.export.domain.ExportResult

/** One project's export sheet: pick a format and the notes switch, export, then share the file once. */
class ExportViewModel(
    private val projectId: String,
    projectName: String,
    private val export: ExportProject,
) : ViewModel() {
    private val _state = MutableStateFlow(ExportSheetState(projectName))
    val state: StateFlow<ExportSheetState> = _state.asStateFlow()

    private val _share = MutableStateFlow<ShareFileUi?>(null)

    /** The file to share; the screen opens the share sheet and calls [onShared]. */
    val share: StateFlow<ShareFileUi?> = _share.asStateFlow()

    fun chooseFormat(format: ExportFormat) = _state.update { it.copy(format = format, problem = null) }

    fun setIncludeNotes(include: Boolean) = _state.update { it.copy(includeNotes = include) }

    fun export() {
        val current = _state.value.takeUnless { it.working } ?: return
        _state.value = current.copy(working = true, problem = null)
        viewModelScope.launch {
            val result = try {
                export(projectId, current.format, current.includeNotes)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            when (result) {
                is ExportResult.Ready -> {
                    _share.value = ShareFileUi(result.file.name, result.file.mimeType, result.file.location)
                    _state.update { it.copy(working = false) }
                }
                ExportResult.ProjectGone -> _state.update { it.copy(working = false, problem = ExportProblemUi.ProjectGone) }
                null -> _state.update { it.copy(working = false, problem = ExportProblemUi.WriteFailed) }
            }
        }
    }

    fun onShared() {
        _share.value = null
    }

    companion object {
        fun factory(projectId: String, projectName: String, export: () -> ExportProject): ViewModelProvider.Factory = viewModelFactory {
            initializer { ExportViewModel(projectId, projectName, export()) }
        }
    }
}
