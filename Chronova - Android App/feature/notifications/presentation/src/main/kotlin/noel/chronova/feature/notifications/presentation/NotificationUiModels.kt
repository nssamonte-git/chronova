package noel.chronova.feature.notifications.presentation

import noel.chronova.core.domain.notifications.NotificationCategory
import noel.chronova.core.domain.notifications.NotificationFilter
import noel.chronova.core.domain.notifications.NotificationKind

/** One notification, as the page and the notification shade show it. */
data class NotificationUi(
    val id: Long,
    val kind: NotificationKind,
    /** The details its wording fills in (counts, task titles, a failure reason). */
    val args: Map<String, String>,
    val projectId: String?,
    val projectName: String?,
    val createdAt: Long,
    val read: Boolean,
    /** An assistant change that can still be undone from here. */
    val canUndo: Boolean,
)

enum class NotificationsMessage { UNDONE, CANNOT_UNDO }

/** A project to filter by: the ones notifications mention, named as they were then. */
data class ProjectChoice(val id: String, val name: String)

/** The notifications page: what it lists and the filters it shows. */
data class NotificationsPageUi(
    val shown: List<NotificationUi> = emptyList(),
    val filter: NotificationFilter = NotificationFilter(),
    /** No notifications at all (not just none through the filters). */
    val isEmpty: Boolean = true,
    val choices: List<ProjectChoice> = emptyList(),
    /** Projects that still exist, so "Open project" isn't offered for a deleted one. */
    val liveProjectIds: Set<String> = emptySet(),
)

/** What each control on the notifications page does. */
data class NotificationsActions(
    val onBack: () -> Unit = {},
    val onOpenProject: (String) -> Unit = {},
    val onMarkRead: (Long) -> Unit = {},
    val onUndo: (NotificationUi) -> Unit = {},
    val onMarkAllRead: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onSetCategory: (NotificationCategory?) -> Unit = {},
    val onToggleUnreadOnly: () -> Unit = {},
    val onSetProject: (String?) -> Unit = {},
    val onToggleOrder: () -> Unit = {},
)
