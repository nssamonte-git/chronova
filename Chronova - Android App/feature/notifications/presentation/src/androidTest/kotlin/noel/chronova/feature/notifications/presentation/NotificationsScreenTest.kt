package noel.chronova.feature.notifications.presentation

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.model.Project
import noel.chronova.core.testing.FakeNotificationRepository
import noel.chronova.core.testing.FakeProjectRepository
import noel.chronova.feature.notifications.domain.ClearNotifications
import noel.chronova.feature.notifications.domain.MarkAllNotificationsRead
import noel.chronova.feature.notifications.domain.MarkNotificationRead
import noel.chronova.feature.notifications.domain.NotificationUseCases
import noel.chronova.feature.notifications.domain.UndoAssistantChange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import noel.chronova.core.ui.R as CoreUiR

/**
 * The notifications page, rendered on a device (this module's own test app) from the real
 * [NotificationsViewModel] over in-memory repositories. Only visible text and semantics are checked.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val zone = ZoneOffset.UTC
    private val office = Project("office", "Office move", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 20))
    private val projects = FakeProjectRepository(listOf(office))
    private val notifications = FakeNotificationRepository()
    private val opened = mutableListOf<String>()
    private val unread get() = res.getString(R.string.notifications_unread)

    private fun add(
        kind: NotificationKind,
        args: Map<String, String> = emptyMap(),
        projectId: String? = null,
        projectName: String? = null,
    ) = runBlocking { notifications.add(kind, args, projectId, projectName) }

    private fun show() {
        val viewModel = NotificationsViewModel(
            notifications,
            projects,
            NotificationUseCases(
                markRead = MarkNotificationRead(notifications),
                markAllRead = MarkAllNotificationsRead(notifications),
                clear = ClearNotifications(notifications),
                undo = UndoAssistantChange(notifications, ApplyScheduleChanges(projects, zone)),
            ),
        )
        compose.setContent {
            ChronovaTheme { NotificationsScreen(viewModel, onBack = {}, onOpenProject = { opened += it }) }
        }
    }

    /** The row showing [text], scrolled into view (the list is lazy). */
    private fun row(text: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text)
    }

    private fun unreadCount() = compose.onAllNodesWithContentDescription(unread, substring = true).fetchSemanticsNodes().size

    @Test
    fun eachKindHasItsOwnWording() {
        val expected = NotificationKind.entries.associateWith { kind ->
            when (kind) {
                NotificationKind.SYNCED -> res.getString(R.string.notif_synced) to
                    res.getQuantityString(R.plurals.notif_sent, 2, 2) + " · " + res.getQuantityString(R.plurals.notif_received, 1, 1)
                NotificationKind.SYNC_FAILED -> res.getString(R.string.notif_sync_failed) to res.getString(R.string.notif_sync_failed_offline)
                NotificationKind.SYNC_WAITING -> res.getString(R.string.notif_waiting) to res.getQuantityString(R.plurals.notif_waiting_body, 3, 3)
                NotificationKind.SYNC_CONFLICT -> res.getString(R.string.notif_conflict) to res.getQuantityString(R.plurals.notif_conflict_body, 1, 1)
                NotificationKind.SYNC_AVAILABLE -> res.getString(R.string.notif_sync_available) to res.getString(R.string.notif_sync_available_body)
                NotificationKind.ASSISTANT_APPLIED -> res.getString(R.string.notif_assistant, "Office move") to
                    res.getQuantityString(R.plurals.notif_assistant_body, 2, 2, "Build, Paint")
                NotificationKind.ASSISTANT_CLASH -> res.getString(R.string.notif_clash) to
                    res.getString(R.string.notif_clash_link, "Paint", "Build") + " · " +
                    res.getQuantityString(R.plurals.notif_assistant_body, 1, 1, "Paint")
                NotificationKind.SIGNED_IN -> res.getString(R.string.notif_signed_in) to res.getString(R.string.notif_signed_in_body, "ana@example.com")
                NotificationKind.SESSION_ENDED -> res.getString(R.string.notif_session_ended) to res.getString(CoreUiR.string.account_session_ended)
                NotificationKind.PASSWORD_CHANGED -> res.getString(R.string.notif_password_changed) to res.getString(R.string.notif_password_changed_body)
                NotificationKind.SIGNED_IN_ELSEWHERE -> res.getString(R.string.notif_signed_in_elsewhere) to res.getString(R.string.notif_signed_in_elsewhere_body)
            }
        }
        add(NotificationKind.SYNCED, mapOf("sent" to "2", "received" to "1"))
        add(NotificationKind.SYNC_FAILED, mapOf("reason" to SyncFailure.OFFLINE.name))
        add(NotificationKind.SYNC_WAITING, mapOf("count" to "3"))
        add(NotificationKind.SYNC_CONFLICT, mapOf("count" to "1"))
        add(NotificationKind.SYNC_AVAILABLE)
        add(NotificationKind.ASSISTANT_APPLIED, mapOf("count" to "2", "tasks" to "Build\nPaint"), "office", "Office move")
        add(
            NotificationKind.ASSISTANT_CLASH,
            mapOf("count" to "1", "tasks" to "Paint", "clash" to "link", "first" to "Paint", "second" to "Build"),
        )
        add(NotificationKind.SIGNED_IN, mapOf("email" to "ana@example.com"))
        add(NotificationKind.SESSION_ENDED)
        add(NotificationKind.PASSWORD_CHANGED)
        add(NotificationKind.SIGNED_IN_ELSEWHERE)
        show()

        expected.forEach { (kind, words) ->
            val (title, body) = words
            row(body).assertIsDisplayed()
            compose.onNodeWithText(title).assertIsDisplayed()
            assertEquals("$kind", true, title.isNotBlank() && body.isNotBlank())
        }
        // Every kind reads differently.
        assertEquals(expected.size, expected.values.toSet().size)
        compose.onNodeWithText(res.getString(R.string.notifications_empty)).assertDoesNotExist()
    }

    @Test
    fun eachSyncFailureSaysWhatToDo() {
        val reasons = mapOf(
            SyncFailure.OFFLINE to R.string.notif_sync_failed_offline,
            SyncFailure.BUSY to R.string.notif_sync_failed_busy,
            SyncFailure.SIGN_IN_AGAIN to R.string.notif_sync_failed_sign_in,
            SyncFailure.SERVER to R.string.notif_sync_failed_server,
        )
        reasons.keys.forEach { add(NotificationKind.SYNC_FAILED, mapOf("reason" to it.name)) }
        show()
        reasons.values.forEach { row(res.getString(it)).assertIsDisplayed() }
    }

    @Test
    fun saysSoWhenThereAreNone() {
        show()
        compose.onNodeWithText(res.getString(R.string.notifications_empty)).assertIsDisplayed()
    }

    @Test
    fun unreadOnlyHidesTheReadOnes() {
        add(NotificationKind.SIGNED_IN, mapOf("email" to "ana@example.com"))
        add(NotificationKind.PASSWORD_CHANGED)
        add(NotificationKind.SYNC_WAITING, mapOf("count" to "2"))
        runBlocking { notifications.markRead(2) } // the password change was already seen
        show()
        val passwordChanged = res.getString(R.string.notif_password_changed)
        compose.onNodeWithText(passwordChanged).assertIsDisplayed()
        assertEquals(2, unreadCount())

        val filter = compose.onNode(hasText(res.getString(R.string.notifications_unread_only)) and hasClickAction())
        filter.performClick()
        compose.onNodeWithText(passwordChanged).assertDoesNotExist()
        compose.onNodeWithText(res.getString(R.string.notif_signed_in)).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.notif_waiting)).assertIsDisplayed()

        filter.performClick()
        compose.onNodeWithText(passwordChanged).assertIsDisplayed()
    }

    @Test
    fun unreadOnlyWithNothingUnreadSaysNothingMatches() {
        add(NotificationKind.PASSWORD_CHANGED)
        runBlocking { notifications.markAllRead() }
        show()
        compose.onNode(hasText(res.getString(R.string.notifications_unread_only)) and hasClickAction()).performClick()
        compose.onNodeWithText(res.getString(R.string.notifications_empty_filtered)).assertIsDisplayed()
    }

    @Test
    fun openingANotificationMarksItRead() {
        add(NotificationKind.SIGNED_IN, mapOf("email" to "ana@example.com"))
        add(NotificationKind.PASSWORD_CHANGED)
        show()
        assertEquals(2, unreadCount())

        compose.onNodeWithText(res.getString(R.string.notif_password_changed)).performClick()
        compose.waitUntil(5_000) { unreadCount() == 1 }
        // The one left unread is the other one.
        compose.onAllNodesWithContentDescription(res.getString(R.string.notif_signed_in), substring = true)
            .fetchSemanticsNodes().let { assertEquals(1, it.size) }
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun openProjectOpensItAndMarksTheNotificationRead() {
        add(NotificationKind.ASSISTANT_APPLIED, mapOf("count" to "1", "tasks" to "Build"), "office", "Office move")
        show()
        assertEquals(1, unreadCount())
        compose.onNode(hasText(res.getString(R.string.notifications_open_project)) and hasClickAction()).performClick()
        compose.waitUntil(5_000) { unreadCount() == 0 }
        assertEquals(listOf("office"), opened)
    }

    @Test
    fun aDeletedProjectCannotBeOpened() {
        add(NotificationKind.ASSISTANT_APPLIED, mapOf("count" to "1", "tasks" to "Build"), "gone", "Old project")
        show()
        compose.onNodeWithText(res.getString(R.string.notif_assistant, "Old project")).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.notifications_open_project)).assertDoesNotExist()
    }
}
