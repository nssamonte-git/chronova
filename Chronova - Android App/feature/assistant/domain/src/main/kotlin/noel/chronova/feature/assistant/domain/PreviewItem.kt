package noel.chronova.feature.assistant.domain

import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.core.domain.schedule.ScheduleState

/** One line of a change preview, in the user's terms. */
sealed interface PreviewItem {
    /** A task's times, title and/or progress change. */
    data class Changed(val before: SchedulerTask, val after: SchedulerTask) : PreviewItem {
        val renamed: Boolean get() = before.title != after.title
        val moved: Boolean get() = before.startMillis != after.startMillis || before.endMillis != after.endMillis
        val progressed: Boolean get() = before.progress != after.progress
    }

    data class Added(val task: SchedulerTask) : PreviewItem

    data class Removed(val task: SchedulerTask) : PreviewItem

    data class Linked(val predecessor: String, val successor: String) : PreviewItem

    data class Unlinked(val predecessor: String, val successor: String) : PreviewItem

    companion object {
        /**
         * The preview for [changes] planned against [state]. Links removed only
         * because their task is deleted aren't listed separately.
         */
        fun from(changes: List<ScheduleChange>, state: ScheduleState): List<PreviewItem> {
            val titles = state.tasks.associate { it.id to it.title }.toMutableMap()
            changes.filterIsInstance<ScheduleChange.CreateTask>().forEach { titles[it.task.id] = it.task.title }
            val deleted = changes.filterIsInstance<ScheduleChange.DeleteTask>().map { it.task.id }.toSet()
            fun title(id: String) = titles[id] ?: "?"
            return changes.mapNotNull { change ->
                when (change) {
                    is ScheduleChange.UpdateTask -> PreviewItem.Changed(change.before, change.after)
                    is ScheduleChange.CreateTask -> PreviewItem.Added(change.task)
                    is ScheduleChange.DeleteTask -> PreviewItem.Removed(change.task)
                    is ScheduleChange.AddLink -> PreviewItem.Linked(title(change.link.predecessorId), title(change.link.successorId))
                    is ScheduleChange.RemoveLink -> {
                        val link = change.link
                        if (link.predecessorId in deleted || link.successorId in deleted) {
                            null
                        } else {
                            PreviewItem.Unlinked(title(link.predecessorId), title(link.successorId))
                        }
                    }
                }
            }
        }
    }
}
