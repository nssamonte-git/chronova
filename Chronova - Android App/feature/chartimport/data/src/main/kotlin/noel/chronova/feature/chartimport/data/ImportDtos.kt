package noel.chronova.feature.chartimport.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import noel.chronova.feature.chartimport.domain.ImportedSchedule
import noel.chronova.feature.chartimport.domain.ImportedTask

/** `POST /v1/import/image`'s answer (docs/import-api.md). */
@Serializable
internal data class ImportResponseDto(
    val result: String,
    val dates: String,
    val project: ProjectDto,
    val tasks: List<TaskDto>,
    val uncertain: List<String> = emptyList(),
) {
    @Serializable
    data class ProjectDto(val name: String = "", val startDate: String? = null, val endDate: String? = null, val days: Int? = null)

    @Serializable
    data class TaskDto(
        val ref: String,
        val title: String,
        val startDate: String? = null,
        val endDate: String? = null,
        val startDay: Int? = null,
        val endDay: Int? = null,
        /** The hours written on a one-day task, "HH:MM"; missing from a server from before them. */
        val startTime: String? = null,
        val endTime: String? = null,
        val milestone: Boolean = false,
        val progress: Int = 0,
        val predecessorRefs: List<String> = emptyList(),
    )

    /** The domain's view; an answer this app doesn't understand throws [IllegalArgumentException]. */
    fun toDomain(): ImportedSchedule = ImportedSchedule(
        result = when (result) {
            "ok" -> ImportedSchedule.Result.OK
            "not_a_schedule" -> ImportedSchedule.Result.NOT_A_SCHEDULE
            "unreadable" -> ImportedSchedule.Result.UNREADABLE
            else -> throw IllegalArgumentException("result")
        },
        dates = when (dates) {
            "absolute" -> ImportedSchedule.Dates.ABSOLUTE
            "relative" -> ImportedSchedule.Dates.RELATIVE
            else -> throw IllegalArgumentException("dates")
        },
        projectName = project.name,
        startDate = project.startDate?.let(::date),
        endDate = project.endDate?.let(::date),
        days = project.days,
        tasks = tasks.map { task ->
            require(task.progress in 0..100)
            val start = task.startTime?.let(::time)
            val end = task.endTime?.let(::time)
            val hours = start != null && end != null
            ImportedTask(
                task.ref, task.title, task.startDate?.let(::date), task.endDate?.let(::date), task.startDay, task.endDay,
                task.milestone, task.progress, task.predecessorRefs, start.takeIf { hours }, end.takeIf { hours },
            )
        },
        uncertain = uncertain,
    )

    /** Hours are a detail: a time this app can't read leaves the task on its day rather than failing the import. */
    private fun time(text: String): LocalTime? = try {
        LocalTime.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun date(text: String): LocalDate = try {
        LocalDate.parse(text)
    } catch (error: DateTimeParseException) {
        throw IllegalArgumentException("date", error)
    }
}
