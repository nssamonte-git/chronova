package noel.chronova.feature.notifications.presentation

import noel.chronova.core.domain.notifications.AppNotification

// Domain to screen: the page and the shade never see the domain notification.

internal fun AppNotification.toUi() = NotificationUi(
    id = id,
    kind = kind,
    args = args,
    projectId = projectId,
    projectName = projectName,
    createdAt = createdAt,
    read = read,
    canUndo = canUndo,
)
