package noel.chronova.feature.notifications.presentation

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
