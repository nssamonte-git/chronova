package noel.chronova.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import noel.chronova.core.domain.notifications.AppNotification
import noel.chronova.core.domain.notifications.NotificationCategory
import noel.chronova.core.domain.notifications.NotificationFilter
import noel.chronova.feature.notifications.domain.NotificationUseCases
import noel.chronova.core.domain.notifications.NotificationQueries
import noel.chronova.core.domain.repository.ProjectQueries

enum class NotificationsMessage { UNDONE, CANNOT_UNDO }

/** A project to filter by: the ones notifications mention, named as they were then. */
data class ProjectChoice(val id: String, val name: String)

class NotificationsViewModel(
    notifications: NotificationQueries,
    projects: ProjectQueries,
    private val useCases: NotificationUseCases,
) : ViewModel() {
    private val _filter = MutableStateFlow(NotificationFilter())
    val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

    private val all: StateFlow<List<AppNotification>> =
        notifications.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The notifications the filters let through, in the chosen order. */
    val shown: StateFlow<List<AppNotification>> =
        combine(all, _filter) { list, filter -> filter.apply(list) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isEmpty: StateFlow<Boolean> = all.map { it.isEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val projectChoices: StateFlow<List<ProjectChoice>> = all.map { list ->
        list.mapNotNull { n -> n.projectId?.let { ProjectChoice(it, n.projectName ?: it) } }.distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Projects that still exist, so "Open project" isn't offered for a deleted one. */
    val liveProjectIds: StateFlow<Set<String>> =
        projects.observeProjects().map { list -> list.map { it.project.id }.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _message = MutableStateFlow<NotificationsMessage?>(null)
    val message: StateFlow<NotificationsMessage?> = _message.asStateFlow()

    fun setCategory(category: NotificationCategory?) = _filter.update { it.copy(category = category) }
    fun toggleUnreadOnly() = _filter.update { it.copy(unreadOnly = !it.unreadOnly) }
    fun setProject(projectId: String?) = _filter.update { it.copy(projectId = projectId) }
    fun toggleOrder() = _filter.update { it.copy(newestFirst = !it.newestFirst) }

    fun markRead(id: Long) {
        viewModelScope.launch { useCases.markRead(id) }
    }

    fun markAllRead() {
        viewModelScope.launch { useCases.markAllRead() }
    }

    fun clear() {
        viewModelScope.launch { useCases.clear() }
    }

    /** Reverses an assistant change, unless its tasks have changed since. */
    fun undo(notification: AppNotification) {
        viewModelScope.launch {
            val undone = useCases.undo(notification)
            _message.value = if (undone) NotificationsMessage.UNDONE else NotificationsMessage.CANNOT_UNDO
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    companion object {
        fun factory(notifications: NotificationQueries, projects: ProjectQueries, useCases: () -> NotificationUseCases): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { NotificationsViewModel(notifications, projects, useCases()) }
            }
    }
}
