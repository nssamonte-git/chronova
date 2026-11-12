package noel.chronova.feature.notifications.presentation

import noel.chronova.core.domain.notifications.AppNotification
import noel.chronova.core.domain.notifications.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationUiMappersTest {
    @Test
    fun keepsEverythingThePageShows() {
        val notification = AppNotification(
            id = 7,
            kind = NotificationKind.ASSISTANT_APPLIED,
            args = mapOf("tasks" to "Build\nTest"),
            projectId = "p1",
            projectName = "Launch",
            createdAt = 1_000L,
            read = false,
            canUndo = true,
        )
        assertEquals(
            NotificationUi(7, NotificationKind.ASSISTANT_APPLIED, mapOf("tasks" to "Build\nTest"), "p1", "Launch", 1_000L, read = false, canUndo = true),
            notification.toUi(),
        )
    }
}
