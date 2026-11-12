package noel.chronova.core.domain

import java.time.LocalDate
import java.time.ZoneOffset
import noel.chronova.core.model.SchedulerTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectRulesTest {

    private val zone = ZoneOffset.UTC
    private val mar2: LocalDate = LocalDate.of(2026, 3, 2)

    private fun taskOn(date: LocalDate, startHour: Int, endHour: Int) = SchedulerTask(
        id = "t$startHour",
        projectId = "p",
        title = "Task",
        startMillis = date.atTime(startHour, 0).toInstant(zone).toEpochMilli(),
        endMillis = date.atTime(endHour, 0).toInstant(zone).toEpochMilli(),
    )

    private fun validate(name: String = "Launch", start: LocalDate = mar2, end: LocalDate = mar2, tasks: List<SchedulerTask> = emptyList()) =
        ProjectRules.validate(name, start, end, tasks, zone)

    @Test
    fun validProjectHasNoProblem() {
        assertNull(validate(end = mar2.plusDays(6)))
    }

    @Test
    fun blankNameIsAProblem() {
        assertEquals(ProjectProblem.BlankName, validate(name = "   "))
    }

    @Test
    fun endBeforeStartIsAProblem() {
        assertEquals(ProjectProblem.EndBeforeStart, validate(end = mar2.minusDays(1)))
    }

    @Test
    fun maximumLengthIsInclusive() {
        assertNull(validate(end = mar2.plusDays(ProjectRules.MAX_DAYS - 1L)))
        assertEquals(ProjectProblem.TooLong(ProjectRules.MAX_DAYS), validate(end = mar2.plusDays(ProjectRules.MAX_DAYS.toLong())))
    }

    @Test
    fun shrinkingPastTasksIsAProblem() {
        val tasks = listOf(taskOn(mar2, 9, 11), taskOn(mar2.plusDays(2), 9, 11))
        assertNull(validate(end = mar2.plusDays(2), tasks = tasks))
        assertEquals(ProjectProblem.ExcludesTasks(1), validate(end = mar2.plusDays(1), tasks = tasks))
        assertEquals(ProjectProblem.ExcludesTasks(1), validate(start = mar2.plusDays(1), end = mar2.plusDays(2), tasks = tasks))
    }
}
