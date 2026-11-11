package noel.chronova.app.domain.voice

import java.time.LocalDateTime
import java.time.ZoneId
import noel.chronova.app.domain.SampleTasks
import org.junit.Assert.assertEquals
import org.junit.Test

class ExplainedChangeTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val tasks = SampleTasks.tasks(zone)
    private val review = tasks.first { it.id == "review" }

    @Test
    fun aRenamedAndMovedTaskIsTwoChangesInLocalTime() {
        val after = review.copy(title = "Sign-off", startMillis = review.startMillis + 3_600_000, endMillis = review.endMillis + 3_600_000)
        assertEquals(
            listOf(
                ExplainedChange("renamed", task = "Review", title = "Sign-off"),
                ExplainedChange(
                    "moved", task = "Sign-off",
                    fromStart = LocalDateTime.of(2026, 3, 2, 16, 0), fromEnd = LocalDateTime.of(2026, 3, 2, 17, 0),
                    toStart = LocalDateTime.of(2026, 3, 2, 17, 0), toEnd = LocalDateTime.of(2026, 3, 2, 18, 0),
                ),
            ),
            ExplainedChange.from(listOf(PreviewItem.Changed(review, after)), zone),
        )
    }

    @Test
    fun everyOtherKindKeepsOnlyItsFields() {
        assertEquals(
            listOf(
                ExplainedChange("added", task = "Review", toStart = LocalDateTime.of(2026, 3, 2, 16, 0), toEnd = LocalDateTime.of(2026, 3, 2, 17, 0)),
                ExplainedChange("removed", task = "Review"),
                ExplainedChange("linked", predecessor = "A", successor = "B"),
                ExplainedChange("unlinked", predecessor = "A", successor = "B"),
            ),
            ExplainedChange.from(
                listOf(PreviewItem.Added(review), PreviewItem.Removed(review), PreviewItem.Linked("A", "B"), PreviewItem.Unlinked("A", "B")),
                zone,
            ),
        )
    }
}
