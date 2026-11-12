package noel.chronova.feature.projects.presentation

import java.time.LocalDate
import noel.chronova.core.domain.ProjectProblem
import noel.chronova.core.model.Project
import noel.chronova.core.model.ProjectSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectUiMappersTest {
    @Test
    fun summaryBecomesARow() {
        val project = Project("p1", "Launch", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9))
        assertEquals(
            ProjectRowUi("p1", "Launch", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9), taskCount = 3),
            ProjectSummary(project, taskCount = 3).toUi(),
        )
    }

    @Test
    fun everyProblemKeepsItsDetail() {
        assertEquals(ProjectProblemUi.BlankName, ProjectProblem.BlankName.toUi())
        assertEquals(ProjectProblemUi.EndBeforeStart, ProjectProblem.EndBeforeStart.toUi())
        assertEquals(ProjectProblemUi.TooLong(366), ProjectProblem.TooLong(366).toUi())
        assertEquals(ProjectProblemUi.ExcludesTasks(4), ProjectProblem.ExcludesTasks(4).toUi())
    }
}
