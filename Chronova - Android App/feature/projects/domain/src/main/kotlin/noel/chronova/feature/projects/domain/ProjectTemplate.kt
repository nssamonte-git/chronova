package noel.chronova.feature.projects.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A project offered as a starting point: its name and length, for the sheet. */
data class ProjectTemplate(val projectId: String, val name: String, val startDate: LocalDate, val endDate: LocalDate) {
    val lengthDays: Long get() = ChronoUnit.DAYS.between(startDate, endDate)
}
