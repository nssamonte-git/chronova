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
fun AssistantAskingAboutAChange() = ChronovaTheme {
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

/** The assistant listening, with nothing heard yet. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun AssistantListening() = ChronovaTheme {
    AssistantOverlay(
        state = AssistantUiState(availability = AssistantAvailability.READY, open = true, mode = OrbMode.Listening, noticeAccepted = true),
        zone = zone,
        level = { 0.4f },
        onClose = {},
    )
}
