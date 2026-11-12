package noel.chronova.feature.assistant.presentation

import noel.chronova.feature.assistant.domain.PreviewItem
import noel.chronova.feature.assistant.domain.ScheduleWarning

// Domain to screen: the orb shows titles and times, never the domain's tasks or changes.

internal fun PreviewItem.toUi(): ProposalLineUi = when (this) {
    is PreviewItem.Changed -> ProposalLineUi.Changed(
        titleBefore = before.title,
        title = after.title,
        startBefore = before.startMillis,
        endBefore = before.endMillis,
        start = after.startMillis,
        end = after.endMillis,
    )
    is PreviewItem.Added -> ProposalLineUi.Added(task.title, task.startMillis, task.endMillis)
    is PreviewItem.Removed -> ProposalLineUi.Removed(task.title)
    is PreviewItem.Linked -> ProposalLineUi.Linked(predecessor, successor)
    is PreviewItem.Unlinked -> ProposalLineUi.Unlinked(predecessor, successor)
}

internal fun ScheduleWarning.toUi(): WarningUi = when (this) {
    is ScheduleWarning.Overlap -> WarningUi.Overlap(task, other)
    is ScheduleWarning.BrokenLink -> WarningUi.BrokenLink(predecessor, successor)
}
