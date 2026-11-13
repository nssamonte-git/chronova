package noel.chronova.feature.timeline.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import noel.chronova.core.domain.schedule.CriticalPath
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TimeScale
import noel.chronova.core.domain.schedule.ApplyResult
import noel.chronova.feature.timeline.domain.DependentsPlan
import noel.chronova.feature.timeline.domain.ProjectSchedule
import noel.chronova.feature.timeline.domain.PushResult
import noel.chronova.feature.timeline.domain.TimelineUseCases

/**
 * One project's timeline. Tasks come from the repository (the source of truth);
 * view configuration and selection are screen state. Gestures commit a finished
 * edit once, on drag end, and the repository flow brings it back.
 */
class ProjectTimelineViewModel(
    private val projectId: String,
    private val useCases: TimelineUseCases,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {

    private val _notice = MutableStateFlow<TimelineNotice?>(null)

    /** A one-off message for the screen, cleared by [dismissNotice]. */
    val notice: StateFlow<TimelineNotice?> = _notice.asStateFlow()

    private val settings = TimelineViewSettings(projectId, useCases, viewModelScope)
    private val selectedTaskId = MutableStateFlow<String?>(null)

    /** The open search bar's filter, or null when it is closed. Never saved: it ends with the screen. */
    private val search = MutableStateFlow<TaskFilter?>(null)

    private val _undoOffer = MutableStateFlow<UndoOffer?>(null)

    /** The last change set that can be undone, offered until [dismissUndo]. */
    val undoOffer: StateFlow<UndoOffer?> = _undoOffer.asStateFlow()

    private val _pushOffer = MutableStateFlow<PushOfferUi?>(null)

    /** Tasks that now start before a moved task ends, offered to be pushed; see [pushDependents]. */
    val pushOffer: StateFlow<PushOfferUi?> = _pushOffer.asStateFlow()

    private val _pushBlocked = MutableStateFlow<PushBlockedUi?>(null)

    /** Pushing them would run past the project's end; said instead of offering. */
    val pushBlocked: StateFlow<PushBlockedUi?> = _pushBlocked.asStateFlow()

    /** The task whose dependents are on offer. */
    private var pushTaskId: String? = null

    /** What undoing the offered change applies. */
    private var undoChanges: List<ScheduleChange> = emptyList()
    private var undoOffers = 0L

    /** The schedule as the domain has it, behind what the screen shows; edits are made against it. */
    private var schedule: ProjectSchedule? = null

    private val editor = TaskEditor(
        projectId, useCases, zone, today, viewModelScope,
        schedule = { schedule },
        timeline = { readyTimeline() },
        onAdded = { selectedTaskId.value = it },
        onSaved = { offerPush(it) },
        onDeleted = { changes -> offerUndo(UndoKind.TaskDeleted, changes) },
        onRefused = { _notice.value = TimelineNotice.EditNotSaved },
    )

    /** The add/edit task sheet, or null when closed. */
    val taskEditor: StateFlow<TaskEditorState?> = editor.state

    val state: StateFlow<TimelineScreenState> = combine(
        useCases.observeSchedule(projectId),
        settings.config,
        selectedTaskId,
        search,
    ) { schedule, config, selected, filter ->
        if (schedule == null) {
            TimelineScreenState.NotFound
        } else if (config == null) {
            // Waiting for saved settings, so the first frame is already at the right scale and zoom.
            TimelineScreenState.Loading
        } else {
            this.schedule = schedule
            val (project, tasks, dependencies) = schedule
            val path = CriticalPath.of(tasks, dependencies)
            val all = tasks.map { it.toUi(path.slackMillis[it.id]) }
            val shown = if (filter == null) all else all.filter(filter::matches)
            TimelineScreenState.Ready(
                TimelineUiState(
                    projectName = project.name,
                    tasks = shown,
                    window = project.window(zone),
                    zone = zone,
                    config = config,
                    // A selection whose task disappeared, or that the search hides, is dropped.
                    selectedTaskId = selected?.takeIf { id -> shown.any { it.id == id } },
                    search = filter?.let { TaskSearchUi(it, matches = shown.size, total = all.size) },
                    dependencies = tasks.associateBy { it.id }.let { byId -> dependencies.map { it.toUi(byId, path::isCritical) } },
                    finishMillis = path.finishMillis,
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimelineScreenState.Loading)

    /** Selects a task (shows its resize handles); null clears the selection. */
    fun selectTask(taskId: String?) {
        selectedTaskId.value = taskId
    }

    /**
     * Saves a dragged or resized task through the shared apply path. [original] is
     * the task as the drag started from; if it changed in the meantime (e.g. a
     * sync), the edit is refused and the user told. Tasks that aren't in this
     * project are ignored.
     */
    fun commitEdit(original: TaskUi, updated: TaskUi) {
        if (state.value !is TimelineScreenState.Ready) return
        if (original.id != updated.id || original == updated) return
        val current = schedule?.tasks?.firstOrNull { it.id == updated.id } ?: return
        // The domain task as the drag started from: what the screen showed then, the rest as stored.
        val from = current.copy(title = original.title, startMillis = original.startMillis, endMillis = original.endMillis, color = original.color)
        val to = from.copy(startMillis = updated.startMillis, endMillis = updated.endMillis)
        viewModelScope.launch {
            when (useCases.commitEdit(projectId, from, to)) {
                is ApplyResult.Applied -> offerPush(to.id)
                is ApplyResult.Rejected -> _notice.value = TimelineNotice.EditNotSaved
            }
        }
    }

    /**
     * Marks the selected task done (100%) through the shared apply path and offers
     * undo; refused, like a drag, if the task changed meanwhile.
     */
    fun markSelectedDone() {
        val taskId = selectedTaskId.value ?: return
        val current = schedule?.tasks?.firstOrNull { it.id == taskId }?.takeUnless { it.isDone } ?: return
        viewModelScope.launch {
            when (val result = useCases.commitEdit(projectId, current, current.copy(progress = TaskUi.DONE))) {
                is ApplyResult.Applied -> offerUndo(UndoKind.MarkedDone, result.undo)
                is ApplyResult.Rejected -> _notice.value = TimelineNotice.EditNotSaved
            }
        }
    }

    private fun offerUndo(kind: UndoKind, changes: List<ScheduleChange>, count: Int = 0) {
        undoChanges = changes
        _undoOffer.value = UndoOffer(kind, id = ++undoOffers, count = count)
    }

    /** After [taskId]'s times changed: offer to push what now starts before it ends, or say it can't be. */
    private fun offerPush(taskId: String) {
        viewModelScope.launch {
            when (val plan = useCases.pushDependents.plan(projectId, taskId)) {
                is DependentsPlan.CanPush -> {
                    pushTaskId = taskId
                    _pushOffer.value = PushOfferUi(plan.taskTitle, plan.count, id = ++undoOffers)
                }
                is DependentsPlan.DoesNotFit ->
                    _pushBlocked.value = PushBlockedUi(plan.taskTitle, plan.titles, plan.projectEndMillis, id = ++undoOffers)
                DependentsPlan.NothingToPush -> Unit
            }
        }
    }

    /** Pushes the offered task's dependents later, just enough, and offers undo. */
    fun pushDependents() {
        val taskId = pushTaskId ?: return
        val offer = _pushOffer.value ?: return
        _pushOffer.value = null
        pushTaskId = null
        viewModelScope.launch {
            when (val result = useCases.pushDependents.push(projectId, taskId)) {
                is PushResult.Pushed -> offerUndo(UndoKind.PushedDependents, result.undo, result.count)
                is PushResult.DoesNotFit ->
                    _pushBlocked.value = PushBlockedUi(offer.taskTitle, result.titles, result.projectEndMillis, id = ++undoOffers)
                PushResult.Refused -> _notice.value = TimelineNotice.EditNotSaved
                PushResult.NothingToPush -> Unit
            }
        }
    }

    fun dismissPushOffer() {
        _pushOffer.value = null
        pushTaskId = null
    }

    fun dismissPushBlocked() {
        _pushBlocked.value = null
    }

    // The search bar (see [TaskFilter]).

    fun openSearch() {
        if (search.value == null) search.value = TaskFilter()
    }

    /** Closing the bar shows every task again. */
    fun closeSearch() {
        search.value = null
    }

    fun onSearchQuery(query: String) = updateSearch { it.copy(query = query) }

    fun toggleColorFilter(color: TaskColor) = updateSearch { it.copy(colors = it.colors.toggled(color)) }

    fun toggleKindFilter(kind: TaskKind) = updateSearch { it.copy(kinds = it.kinds.toggled(kind)) }

    fun clearSearch() = updateSearch { TaskFilter() }

    private fun updateSearch(change: (TaskFilter) -> TaskFilter) {
        val next = change(search.value ?: return)
        search.value = next
        // A selected task the search hides stays unselected when the search is cleared.
        val selected = schedule?.tasks?.firstOrNull { it.id == selectedTaskId.value }?.toUi()
        if (selected != null && !next.matches(selected)) selectedTaskId.value = null
    }

    private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

    fun dismissNotice() {
        _notice.value = null
    }

    // The task editor's intents (see [TaskEditor]).

    fun startAddTask() = editor.startAdd()

    fun startEditTask(taskId: String) = editor.startEdit(taskId)

    /** Adds or removes a predecessor in the editor; forbidden ones are ignored. */
    fun onTogglePredecessor(predecessorId: String) = editor.togglePredecessor(predecessorId)

    fun onTaskTitleChange(title: String) = editor.changeTitle(title)

    /** Moving the start keeps the duration, so the end moves with it. */
    fun onTaskStartChange(startMillis: Long) = editor.changeStart(startMillis)

    fun onTaskEndChange(endMillis: Long) = editor.changeEnd(endMillis)

    fun onTaskColorChange(color: TaskColor) = editor.changeColor(color)

    fun onTaskProgressChange(progress: Int) = editor.changeProgress(progress)

    fun onTaskDoneChange(done: Boolean) = editor.changeDone(done)

    fun onTaskNotesChange(notes: String) = editor.changeNotes(notes)

    fun onTaskMilestoneChange(isMilestone: Boolean) = editor.changeMilestone(isMilestone)

    fun dismissTaskEditor() = editor.dismiss()

    fun saveTask() = editor.save()

    /** Deletes the task being edited, with its links, and offers undo. */
    fun deleteEditingTask() = editor.delete()

    /** Reverses the offered change set, unless those tasks were edited again since. */
    fun undo() {
        if (_undoOffer.value == null) return
        val changes = undoChanges
        _undoOffer.value = null
        undoChanges = emptyList()
        viewModelScope.launch {
            if (useCases.undo(projectId, changes) is ApplyResult.Rejected) _notice.value = TimelineNotice.UndoNotPossible
        }
    }

    fun dismissUndo() {
        _undoOffer.value = null
    }

    private fun readyTimeline(): TimelineUiState? = (state.value as? TimelineScreenState.Ready)?.timeline

    // View settings (see [TimelineViewSettings]): how time is shown and snapped, never task times.

    fun setTimeScale(scale: TimeScale) = settings.setTimeScale(scale)

    fun zoomIn() = settings.zoomIn()

    fun zoomOut() = settings.zoomOut()

    fun resetZoom() = settings.resetZoom()

    fun setSnapMinutes(minutes: Int) = settings.setSnapMinutes(minutes)

    fun changeRowHeight(steps: Int) = settings.changeRowHeight(steps)

    /** Shows or hides the critical path; remembered for this project on this device. */
    fun setShowCriticalPath(show: Boolean) = settings.update { it.copy(showCriticalPath = show) }

    /** One pinch step: zooms at once, without saving every step of the gesture. */
    fun zoomBy(factor: Float) = settings.zoomBy(factor)

    /** The pinch ended: remember the zoom it settled on, once. */
    fun finishZoom() = settings.finishZoom()

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** [projectId] comes from the timeline's navigation argument (the shell reads it). */
        fun factory(projectId: String, useCases: () -> TimelineUseCases): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProjectTimelineViewModel(projectId, useCases()) }
        }
    }
}
