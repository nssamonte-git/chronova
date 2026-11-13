package noel.chronova.core.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationFilterTest {
    private fun n(id: Long, kind: NotificationKind, at: Long, read: Boolean = false, project: String? = null) =
        AppNotification(id, kind, emptyMap(), project, project?.uppercase(), at, read, canUndo = false)

    private val all = listOf(
        n(1, NotificationKind.SYNCED, at = 100, read = true),
        n(2, NotificationKind.ASSISTANT_APPLIED, at = 300, project = "p1"),
        n(3, NotificationKind.SESSION_ENDED, at = 200),
        n(4, NotificationKind.ASSISTANT_CLASH, at = 300, read = true, project = "p2"),
        n(5, NotificationKind.SYNC_FAILED, at = 50),
    )

    private fun ids(filter: NotificationFilter) = filter.apply(all).map { it.id }

    @Test
    fun newestFirstByDefaultWithTiesInTheOrderTheyCame() {
        assertEquals(listOf(4L, 2L, 3L, 1L, 5L), ids(NotificationFilter()))
        assertEquals(listOf(5L, 1L, 3L, 2L, 4L), ids(NotificationFilter(newestFirst = false)))
    }

    @Test
    fun filtersByTypeUnreadAndProjectTogether() {
        assertEquals(listOf(1L, 5L), ids(NotificationFilter(category = NotificationCategory.SYNC)))
        assertEquals(listOf(2L, 3L, 5L), ids(NotificationFilter(unreadOnly = true)))
        assertEquals(listOf(2L), ids(NotificationFilter(category = NotificationCategory.ASSISTANT, unreadOnly = true)))
        assertEquals(listOf(4L), ids(NotificationFilter(projectId = "p2")))
        assertEquals(emptyList<Long>(), ids(NotificationFilter(category = NotificationCategory.ACCOUNT, projectId = "p1")))
    }

    @Test
    fun onlyFailuresEndedSessionsAndOtherDevicesAlertsReachTheShade() {
        assertEquals(
            setOf(NotificationKind.SYNC_FAILED, NotificationKind.SESSION_ENDED, NotificationKind.SYNC_AVAILABLE, NotificationKind.SIGNED_IN_ELSEWHERE),
            NotificationKind.entries.filter { it.important }.toSet(),
        )
    }
}
