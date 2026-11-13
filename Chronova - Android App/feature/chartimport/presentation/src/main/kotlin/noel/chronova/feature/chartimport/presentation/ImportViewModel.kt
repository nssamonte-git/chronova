package noel.chronova.feature.chartimport.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import noel.chronova.core.domain.ProjectProblem
import noel.chronova.feature.chartimport.domain.ImportApiException
import noel.chronova.feature.chartimport.domain.ImportChoices
import noel.chronova.feature.chartimport.domain.ImportOutcome
import noel.chronova.feature.chartimport.domain.ImportPreview
import noel.chronova.feature.chartimport.domain.ImportProblem
import noel.chronova.feature.chartimport.domain.ImportTarget
import noel.chronova.feature.chartimport.domain.ImportUseCases
import noel.chronova.feature.chartimport.domain.ImportedSchedule
import noel.chronova.feature.chartimport.domain.PlannedImport

/**
 * One import: an image (a photo or a picked one) is read on the server, previewed, and applied
 * only on Apply. [projectId] null makes a new project; otherwise the tasks go into that project.
 */
class ImportViewModel(
    private val projectId: String?,
    private val useCases: ImportUseCases,
    private val today: () -> LocalDate,
    /** A new project's name when the chart has no title. */
    private val untitled: String,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportSheetUi>(ImportSheetUi.Choose(newProject = projectId == null, projectName = ""))
    val state: StateFlow<ImportSheetUi> = _state.asStateFlow()

    private val _camera = MutableStateFlow<String?>(null)

    /** A file for the camera app to write into; the screen opens the camera and calls [onPhotoTaken]. */
    val camera: StateFlow<String?> = _camera.asStateFlow()

    private var target: ImportTarget = ImportTarget.NewProject
    private var schedule: ImportedSchedule? = null
    private var selected: Set<String> = emptySet()
    private var outcome: ImportOutcome? = null
    private var pendingPhoto: String? = null
    private var reading: Job? = null

    init {
        if (projectId != null) {
            viewModelScope.launch {
                val current = useCases.loadTarget(projectId)
                if (current == null) {
                    _state.value = ImportSheetUi.Problem(ImportProblemUi.ProjectGone)
                } else {
                    target = ImportTarget.Existing(current.project)
                    _state.update { if (it is ImportSheetUi.Choose) it.copy(projectName = current.project.name) else it }
                }
            }
        }
    }

    fun takePhoto() {
        viewModelScope.launch {
            val uri = useCases.images.newPhotoUri()
            pendingPhoto = uri
            _camera.value = uri
        }
    }

    /** The camera was opened (or couldn't be: [opened] false). */
    fun onCameraOpened(opened: Boolean) {
        _camera.value = null
        if (!opened) _state.value = ImportSheetUi.Problem(ImportProblemUi.NoCamera)
    }

    fun onPhotoTaken(saved: Boolean) {
        val uri = pendingPhoto ?: return
        pendingPhoto = null
        if (saved) read(uri)
    }

    /** A picked image, or null when the picker was closed. */
    fun onImageChosen(uri: String?) {
        if (uri != null) read(uri)
    }

    /** Stops reading and goes back to choosing an image. */
    fun cancelReading() {
        reading?.cancel()
        backToChoose()
    }

    fun backToChoose() {
        _state.value = ImportSheetUi.Choose(newProject = projectId == null, projectName = (target as? ImportTarget.Existing)?.project?.name.orEmpty())
    }

    private fun read(uri: String) {
        if (reading?.isActive == true) return
        _state.value = ImportSheetUi.Reading
        reading = viewModelScope.launch {
            val problem = try {
                val read = useCases.readChart(uri, target)
                when (read.result) {
                    ImportedSchedule.Result.NOT_A_SCHEDULE -> ImportProblemUi.NotASchedule
                    ImportedSchedule.Result.UNREADABLE -> ImportProblemUi.Unreadable
                    ImportedSchedule.Result.OK -> {
                        showPreview(read)
                        null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportApiException) {
                problemFor(e.kind)
            } catch (_: Exception) {
                ImportProblemUi.Unavailable
            } finally {
                useCases.images.clear()
            }
            if (problem != null) _state.value = ImportSheetUi.Problem(problem)
        }
    }

    private fun showPreview(read: ImportedSchedule) {
        schedule = read
        val start = ImportPreview.defaultStart(target, today())
        selected = ImportPreview.defaultSelection(ImportPreview.rows(read, target, start))
        val (projectStart, projectEnd) = ImportPreview.projectDays(read, start)
        val name = when (val current = target) {
            is ImportTarget.Existing -> current.project.name
            ImportTarget.NewProject -> ImportPreview.projectName(read, untitled)
        }
        _state.value = ImportSheetUi.Preview(
            ImportPreviewUi(
                newProject = target == ImportTarget.NewProject,
                projectName = name,
                projectStart = projectStart,
                projectEnd = projectEnd,
                relative = read.dates == ImportedSchedule.Dates.RELATIVE,
                start = start,
                rows = rows(read, start),
                uncertain = read.uncertain,
            ),
        )
    }

    fun toggle(ref: String) {
        selected = if (ref in selected) selected - ref else selected + ref
        updatePreview { it.copy(rows = it.rows.map { row -> row.copy(selected = row.ref in selected) }, problem = null) }
    }

    /** Where a chart without calendar dates starts; a new project moves with it. */
    fun setStart(start: LocalDate) {
        val read = schedule ?: return
        if (target is ImportTarget.Existing) selected = ImportPreview.defaultSelection(ImportPreview.rows(read, target, start))
        val (projectStart, projectEnd) = ImportPreview.projectDays(read, start)
        updatePreview { it.copy(start = start, projectStart = projectStart, projectEnd = projectEnd, rows = rows(read, start), problem = null) }
    }

    fun setName(name: String) = updatePreview { it.copy(projectName = name, problem = null) }

    fun setProjectDays(start: LocalDate, end: LocalDate) = updatePreview { it.copy(projectStart = start, projectEnd = end, problem = null) }

    fun apply() {
        val read = schedule ?: return
        val preview = (_state.value as? ImportSheetUi.Preview)?.preview?.takeUnless { it.applying } ?: return
        updatePreview { it.copy(applying = true, problem = null) }
        viewModelScope.launch {
            val existing = (target as? ImportTarget.Existing)?.let { useCases.loadTarget(it.project.id) }
            if (target is ImportTarget.Existing && existing == null) {
                updatePreview { it.copy(applying = false, problem = ImportPlanProblemUi.ProjectChanged) }
                return@launch
            }
            val choices = ImportChoices(selected, preview.start, preview.projectName, preview.projectStart, preview.projectEnd)
            when (val planned = useCases.plan.plan(read, target, choices, existing)) {
                is PlannedImport.Invalid -> updatePreview { it.copy(applying = false, problem = planned.problem.toUi()) }
                is PlannedImport.Ready -> when (val result = useCases.apply(planned.plan)) {
                    is ImportOutcome.Failed -> updatePreview { it.copy(applying = false, problem = ImportPlanProblemUi.ProjectChanged) }
                    else -> {
                        outcome = result
                        val name = when (val plan = planned.plan) {
                            is noel.chronova.feature.chartimport.domain.ImportPlan.NewProject -> plan.project.name
                            is noel.chronova.feature.chartimport.domain.ImportPlan.AddToProject -> plan.project.name
                        }
                        _state.value = ImportSheetUi.Done(ImportDoneUi(result.projectId, name, result.taskCount, result is ImportOutcome.Created))
                    }
                }
            }
        }
    }

    fun undo() {
        val done = (_state.value as? ImportSheetUi.Done)?.done?.takeIf { it.undone == null } ?: return
        val applied = outcome ?: return
        viewModelScope.launch {
            val undone = useCases.undo(applied)
            outcome = null
            _state.value = ImportSheetUi.Done(done.copy(undone = undone))
        }
    }

    private fun rows(read: ImportedSchedule, start: LocalDate): List<ImportRowUi> =
        ImportPreview.rows(read, target, start).map { row ->
            ImportRowUi(
                row.ref, row.title, row.firstDay, row.lastDay, row.isMilestone, row.progress,
                links = row.predecessorRefs.size, fits = row.fits, selected = row.ref in selected,
            )
        }

    private fun updatePreview(change: (ImportPreviewUi) -> ImportPreviewUi) =
        _state.update { if (it is ImportSheetUi.Preview) ImportSheetUi.Preview(change(it.preview)) else it }

    private fun problemFor(kind: ImportApiException.Kind): ImportProblemUi = when (kind) {
        ImportApiException.Kind.UNAUTHORIZED -> ImportProblemUi.SignInAgain
        ImportApiException.Kind.DISABLED -> ImportProblemUi.SwitchedOff
        ImportApiException.Kind.RATE_LIMITED -> ImportProblemUi.TooMany
        ImportApiException.Kind.BAD_IMAGE -> ImportProblemUi.BadImage
        ImportApiException.Kind.UNAVAILABLE -> ImportProblemUi.Unavailable
        ImportApiException.Kind.NETWORK -> ImportProblemUi.Offline
    }

    private fun ImportProblem.toUi(): ImportPlanProblemUi = when (this) {
        ImportProblem.NothingSelected -> ImportPlanProblemUi.NothingSelected
        is ImportProblem.TaskDoesNotFit -> ImportPlanProblemUi.DoesNotFit(title)
        is ImportProblem.BadProject -> when (val project = problem) {
            ProjectProblem.BlankName -> ImportPlanProblemUi.BlankName
            ProjectProblem.EndBeforeStart -> ImportPlanProblemUi.EndBeforeStart
            is ProjectProblem.TooLong -> ImportPlanProblemUi.TooLong(project.maxDays)
            // Not checked here: a new project has no tasks of its own yet.
            is ProjectProblem.ExcludesTasks -> ImportPlanProblemUi.ProjectChanged
        }
    }

    companion object {
        fun factory(projectId: String?, useCases: () -> ImportUseCases, today: () -> LocalDate, untitled: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { ImportViewModel(projectId, useCases(), today, untitled) } }
    }
}
