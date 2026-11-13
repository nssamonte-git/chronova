package noel.chronova.feature.assistant.presentation

/** One line of what the assistant proposes to change, as the orb shows it. Times are epoch millis. */
sealed interface ProposalLineUi {
    /** A task's times, title and/or progress change. */
    data class Changed(
        val titleBefore: String,
        val title: String,
        val startBefore: Long,
        val endBefore: Long,
        val start: Long,
        val end: Long,
        val progressBefore: Int = 0,
        val progress: Int = 0,
    ) : ProposalLineUi {
        val renamed: Boolean get() = titleBefore != title
        val moved: Boolean get() = startBefore != start || endBefore != end
        val progressed: Boolean get() = progressBefore != progress
    }

    /** A new task, or a new milestone ([start] equals [end]). */
    data class Added(val title: String, val start: Long, val end: Long) : ProposalLineUi {
        val isMilestone: Boolean get() = start == end
    }
    data class Removed(val title: String) : ProposalLineUi
    data class Linked(val predecessor: String, val successor: String) : ProposalLineUi
    data class Unlinked(val predecessor: String, val successor: String) : ProposalLineUi
}

/** A clash the proposal would cause, named under its lines. */
sealed interface WarningUi {
    /** [task] would overlap [other] in time. */
    data class Overlap(val task: String, val other: String) : WarningUi

    /** [successor] would start before [predecessor], which it waits for, ends. */
    data class BrokenLink(val predecessor: String, val successor: String) : WarningUi
}
