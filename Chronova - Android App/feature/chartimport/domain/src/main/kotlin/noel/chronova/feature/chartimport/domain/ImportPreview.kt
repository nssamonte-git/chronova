package noel.chronova.feature.chartimport.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import noel.chronova.core.domain.TaskRules

/** One task of the preview, on calendar days, and whether it fits the project it would go into. */
data class PreviewRow(
    val ref: String,
    val title: String,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val isMilestone: Boolean,
    val progress: Int,
    val predecessorRefs: List<String>,
    /** False when adding to a project whose dates don't hold the task: unticked at first. */
    val fits: Boolean,
    /** The hours the task will get on its one day; null when it takes its whole days. */
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
)

/** Turns the server's answer into the preview's rows and first choices. */
object ImportPreview {
    /** Where a chart with only relative days starts at first: the open project's first day, else today. */
    fun defaultStart(target: ImportTarget, today: LocalDate): LocalDate = when (target) {
        is ImportTarget.Existing -> target.project.startDate
        ImportTarget.NewProject -> today
    }

    /** A new project's first and last days for [start]: the chart's own dates, or [start] onwards for its length. */
    fun projectDays(schedule: ImportedSchedule, start: LocalDate): Pair<LocalDate, LocalDate> =
        if (schedule.dates == ImportedSchedule.Dates.ABSOLUTE && schedule.startDate != null && schedule.endDate != null) {
            schedule.startDate to schedule.endDate
        } else {
            val days = schedule.days ?: ((schedule.tasks.maxOfOrNull { it.endDay ?: 0 } ?: 0) + 1)
            start to start.plusDays(days.toLong() - 1)
        }

    /** A new project's name at first: the chart's title, else [fallback]. */
    fun projectName(schedule: ImportedSchedule, fallback: String): String = schedule.projectName.trim().ifEmpty { fallback }

    /** The rows, in the chart's order; relative days count from [start]. */
    fun rows(schedule: ImportedSchedule, target: ImportTarget, start: LocalDate): List<PreviewRow> = schedule.tasks.mapNotNull { task ->
        val first = day(schedule, task.startDate, task.startDay, start) ?: return@mapNotNull null
        val last = day(schedule, task.endDate, task.endDay, start) ?: return@mapNotNull null
        val fits = when (target) {
            ImportTarget.NewProject -> true
            is ImportTarget.Existing -> !first.isBefore(target.project.startDate) && !last.isAfter(target.project.endDate)
        }
        val hours = hours(task, first, last)
        PreviewRow(task.ref, task.title, first, last, task.isMilestone, task.progress, task.predecessorRefs, fits, hours?.first, hours?.second)
    }

    /** The rows ticked at first: every one that fits. */
    fun defaultSelection(rows: List<PreviewRow>): Set<String> = rows.filter { it.fits }.mapTo(linkedSetOf()) { it.ref }

    /**
     * The hours a task will be placed on: both written on the chart, for a task on one day, and long
     * enough to be a task. Anything else keeps its whole days.
     */
    private fun hours(task: ImportedTask, first: LocalDate, last: LocalDate): Pair<LocalTime, LocalTime>? {
        val start = task.startTime ?: return null
        val end = task.endTime ?: return null
        if (task.isMilestone || first != last) return null
        return (start to end).takeIf { Duration.between(start, end).toMinutes() >= TaskRules.MIN_MINUTES }
    }

    private fun day(schedule: ImportedSchedule, date: LocalDate?, day: Int?, start: LocalDate): LocalDate? = when (schedule.dates) {
        ImportedSchedule.Dates.ABSOLUTE -> date
        ImportedSchedule.Dates.RELATIVE -> day?.let { start.plusDays(it.toLong()) }
    }
}
