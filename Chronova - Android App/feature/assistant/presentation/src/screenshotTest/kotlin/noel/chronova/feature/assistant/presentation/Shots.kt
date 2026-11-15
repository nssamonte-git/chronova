package noel.chronova.feature.assistant.presentation

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDateTime
import java.time.ZoneOffset
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.feature.assistant.presentation.orb.OrbMode

private val zone = ZoneOffset.UTC
private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, 12, hour, minute).toInstant(zone).toEpochMilli()

/** The assistant asking about a change: what it heard, what it says, the proposal, a clash and an alternative. (The 3D orb shows its fallback here.) */
@PreviewTest
@ChronovaScreenshots
@Composable
fun AskingAboutAChange() = ChronovaTheme {
    AssistantOverlay(
        state = AssistantUiState(
            availability = AssistantAvailability.READY,
            open = true,
            mode = OrbMode.Speaking,
            heard = "Move review an hour earlier",
            saying = "Review would move to 3 p.m. and clash with QA testing. Shall I move it to 5 p.m. instead?",
            proposal = listOf(ProposalLineUi.Changed("Review", "Review", at(16), at(17), at(15), at(16))),
            warnings = listOf(WarningUi.Overlap("Review", "QA testing")),
            offer = listOf(ProposalLineUi.Changed("Review", "Review", at(16), at(17), at(17), at(18))),
            noticeAccepted = true,
        ),
        zone = zone,
        level = { 0f },
        onClose = {},
    )
}

private fun day(day: Int) = LocalDateTime.of(2026, 11, day, 0, 0).toInstant(zone).toEpochMilli()

/**
 * Whole-day tasks, as read from a chart, are shown by their days: a one-day task read
 * "Thu 26 Nov 00:00 – 00:00" before, and three days ended "Wed 18 Nov 00:00".
 */
@PreviewTest
@ChronovaScreenshots
@Composable
fun WholeDayTasks() = ChronovaTheme {
    AssistantOverlay(
        state = AssistantUiState(
            availability = AssistantAvailability.READY,
            open = true,
            mode = OrbMode.Speaking,
            heard = "Menu tasting on Wednesday",
            saying = "That clashes with Train staff. Tuesday instead?",
            proposal = listOf(
                ProposalLineUi.Changed("Menu tasting", "Menu tasting", day(26), day(27), day(25), day(26)),
                ProposalLineUi.Added("Hire staff", day(16), day(19)),
            ),
            warnings = listOf(WarningUi.Overlap("Menu tasting", "Train staff")),
            offer = listOf(ProposalLineUi.Changed("Menu tasting", "Menu tasting", day(26), day(27), day(24), day(25))),
            noticeAccepted = true,
        ),
        zone = zone,
        level = { 0f },
        onClose = {},
    )
}

/** The assistant listening, with nothing heard yet. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun Listening() = ChronovaTheme {
    AssistantOverlay(
        state = AssistantUiState(availability = AssistantAvailability.READY, open = true, mode = OrbMode.Listening, noticeAccepted = true),
        zone = zone,
        level = { 0.4f },
        onClose = {},
    )
}

/** The one-time notice before the first conversation. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun FirstUseNotice() = ChronovaTheme { AssistantNotice(onContinue = {}, onDismiss = {}) }
