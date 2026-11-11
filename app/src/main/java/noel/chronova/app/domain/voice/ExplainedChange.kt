package noel.chronova.app.domain.voice

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One change as `/v1/voice/explain` takes it (`docs/voice-api.md`): titles and
 * local times only. Fields a kind doesn't use stay null and aren't sent.
 */
data class ExplainedChange(
    val change: String,
    val task: String? = null,
    val title: String? = null,
    val predecessor: String? = null,
    val successor: String? = null,
    val fromStart: LocalDateTime? = null,
    val fromEnd: LocalDateTime? = null,
    val toStart: LocalDateTime? = null,
    val toEnd: LocalDateTime? = null,
) {
    companion object {
        /** The preview in explain terms; a task both renamed and moved is two changes, the move under its new name. */
        fun from(items: List<PreviewItem>, zone: ZoneId): List<ExplainedChange> = items.flatMap { item ->
            fun local(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
            when (item) {
                is PreviewItem.Changed -> listOfNotNull(
                    if (item.renamed) ExplainedChange("renamed", task = item.before.title, title = item.after.title) else null,
                    if (item.moved) {
                        ExplainedChange(
                            "moved",
                            task = item.after.title,
                            fromStart = local(item.before.startMillis),
                            fromEnd = local(item.before.endMillis),
                            toStart = local(item.after.startMillis),
                            toEnd = local(item.after.endMillis),
                        )
                    } else {
                        null
                    },
                )
                is PreviewItem.Added -> listOf(
                    ExplainedChange("added", task = item.task.title, toStart = local(item.task.startMillis), toEnd = local(item.task.endMillis)),
                )
                is PreviewItem.Removed -> listOf(ExplainedChange("removed", task = item.task.title))
                is PreviewItem.Linked -> listOf(ExplainedChange("linked", predecessor = item.predecessor, successor = item.successor))
                is PreviewItem.Unlinked -> listOf(ExplainedChange("unlinked", predecessor = item.predecessor, successor = item.successor))
            }
        }
    }
}
