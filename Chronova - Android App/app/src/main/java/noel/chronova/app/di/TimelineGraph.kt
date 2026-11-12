package noel.chronova.app.di

import java.time.ZoneId
import noel.chronova.app.AppContainer
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.feature.timeline.domain.CommitTaskEdit
import noel.chronova.feature.timeline.domain.DeleteTask
import noel.chronova.feature.timeline.domain.ForbiddenPredecessors
import noel.chronova.feature.timeline.domain.LoadViewSettings
import noel.chronova.feature.timeline.domain.ObserveProjectSchedule
import noel.chronova.feature.timeline.domain.SaveTask
import noel.chronova.feature.timeline.domain.SaveViewSettings
import noel.chronova.feature.timeline.domain.TimelineUseCases
import noel.chronova.feature.timeline.domain.UndoScheduleChanges
import noel.chronova.feature.timeline.domain.ValidateTask

/** The timeline's wiring. */
class TimelineGraph(private val app: AppContainer) {
    /** The time zone is read when the timeline opens. */
    fun useCases(): TimelineUseCases {
        val repository = app.projectRepository
        val apply = ApplyScheduleChanges(repository, ZoneId.systemDefault())
        return TimelineUseCases(
            observeSchedule = ObserveProjectSchedule(repository),
            commitEdit = CommitTaskEdit(apply),
            validateTask = ValidateTask(),
            forbiddenPredecessors = ForbiddenPredecessors(),
            saveTask = SaveTask(repository),
            deleteTask = DeleteTask(apply),
            undo = UndoScheduleChanges(apply),
            loadViewSettings = LoadViewSettings(repository),
            saveViewSettings = SaveViewSettings(repository),
        )
    }
}
