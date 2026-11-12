package noel.chronova.feature.assistant.presentation

import noel.chronova.core.model.SchedulerTask
import noel.chronova.feature.assistant.domain.PreviewItem
import noel.chronova.feature.assistant.domain.ScheduleWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantUiMappersTest {
    private val review = SchedulerTask("r", "p", "Review", startMillis = 1_000L, endMillis = 5_000L)

    @Test
    fun aChangeKeepsBothTitlesAndBothTimes() {
        val line = PreviewItem.Changed(review, review.copy(title = "Final review", startMillis = 2_000L, endMillis = 6_000L)).toUi()
        assertEquals(ProposalLineUi.Changed("Review", "Final review", 1_000L, 5_000L, 2_000L, 6_000L), line)
        line as ProposalLineUi.Changed
        assertTrue(line.renamed)
        assertTrue(line.moved)
    }

    @Test
    fun aRenameAloneIsNotAMove() {
        val line = PreviewItem.Changed(review, review.copy(title = "Final review")).toUi() as ProposalLineUi.Changed
        assertTrue(line.renamed)
        assertFalse(line.moved)
    }

    @Test
    fun otherLinesKeepWhatTheyName() {
        assertEquals(ProposalLineUi.Added("Review", 1_000L, 5_000L), PreviewItem.Added(review).toUi())
        assertEquals(ProposalLineUi.Removed("Review"), PreviewItem.Removed(review).toUi())
        assertEquals(ProposalLineUi.Linked("Build", "Test"), PreviewItem.Linked("Build", "Test").toUi())
        assertEquals(ProposalLineUi.Unlinked("Build", "Test"), PreviewItem.Unlinked("Build", "Test").toUi())
    }

    @Test
    fun warningsKeepTheirTasks() {
        assertEquals(WarningUi.Overlap("Review", "QA"), ScheduleWarning.Overlap("Review", "QA").toUi())
        assertEquals(WarningUi.BrokenLink("Build", "Test"), ScheduleWarning.BrokenLink("Build", "Test").toUi())
    }
}
