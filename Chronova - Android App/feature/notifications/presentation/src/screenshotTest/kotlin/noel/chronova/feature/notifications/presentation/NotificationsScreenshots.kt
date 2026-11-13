package noel.chronova.feature.notifications.presentation

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDateTime
import java.time.ZoneOffset
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.domain.notifications.NotificationFilter
import noel.chronova.core.domain.notifications.NotificationKind

/** A fixed "now", so the relative times ("5 minutes ago") don't change between runs. */
private val now = LocalDateTime.of(2026, 11, 13, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
private const val MINUTE = 60_000L

private val notifications = listOf(
    NotificationUi(1, NotificationKind.SYNCED, mapOf("sent" to "5", "received" to "2"), null, null, now - 5 * MINUTE, read = false, canUndo = false),
    NotificationUi(
        2, NotificationKind.ASSISTANT_APPLIED, mapOf("count" to "2", "tasks" to "Review\nQA testing"),
        "launch", "Website launch", now - 2 * 60 * MINUTE, read = false, canUndo = true,
    ),
    NotificationUi(3, NotificationKind.SYNC_FAILED, mapOf("reason" to "OFFLINE"), null, null, now - 26 * 60 * MINUTE, read = true, canUndo = false),
    NotificationUi(4, NotificationKind.SIGNED_IN, mapOf("email" to "ana@example.com"), null, null, now - 3 * 24 * 60 * MINUTE, read = true, canUndo = false),
)
private val choices = listOf(ProjectChoice("launch", "Website launch"))

@Composable
private fun Page(page: NotificationsPageUi) = ChronovaTheme {
    NotificationsContent(page = page, actions = NotificationsActions(), now = { now })
}

/** Read and unread notifications of several kinds, one with Undo and Open project. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun NotificationsList() = Page(NotificationsPageUi(notifications, NotificationFilter(), isEmpty = false, choices = choices, liveProjectIds = setOf("launch")))

/** "Unread only" on: the two unread ones. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun NotificationsUnreadOnly() = Page(
    NotificationsPageUi(notifications.filter { !it.read }, NotificationFilter(unreadOnly = true), isEmpty = false, choices = choices, liveProjectIds = setOf("launch")),
)

/** Nothing has happened yet. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun NotificationsEmpty() = Page(NotificationsPageUi())

/** What other devices bring by push: changes to sync and a new sign-in. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun NotificationsFromOtherDevices() = Page(
    NotificationsPageUi(
        listOf(
            NotificationUi(1, NotificationKind.SYNC_AVAILABLE, emptyMap(), null, null, now - 2 * MINUTE, read = false, canUndo = false),
            NotificationUi(2, NotificationKind.SIGNED_IN_ELSEWHERE, emptyMap(), null, null, now - 60 * MINUTE, read = false, canUndo = false),
        ),
        NotificationFilter(), isEmpty = false, choices = choices, liveProjectIds = setOf("launch"),
    ),
)
