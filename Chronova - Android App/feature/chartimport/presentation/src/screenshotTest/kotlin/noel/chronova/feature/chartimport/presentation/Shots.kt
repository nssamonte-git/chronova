package noel.chronova.feature.chartimport.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate
import java.time.LocalTime
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme

private val monday = LocalDate.of(2026, 11, 16)

private fun row(ref: String, title: String, from: Int, to: Int, links: Int = 0, fits: Boolean = true, milestone: Boolean = false) =
    ImportRowUi(ref, title, monday.plusDays(from.toLong()), monday.plusDays(to.toLong()), milestone, 0, links, fits, selected = fits)

/** The test chart's first steps, as read. */
private val steps = listOf(
    row("t1", "Initial Client Consult", 0, 6),
    row("t2", "Project Design Inquiry", 0, 13),
    row("t3", "Initial Design Concept", 14, 20, links = 1),
    row("t4", "Release Design For Bid", 21, 27, links = 1),
    row("m1", "Design approved", 27, 27, milestone = true),
)

@Composable
// On the sheet's colour, with room above the title where the sheet has its drag handle.
private fun Sheet(state: ImportSheetUi) = ChronovaTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.padding(top = 24.dp)) { ImportSheetContent(state, ImportActions()) }
    }
}

/** As it opens from "New project": take a photo or choose an image. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun Choose() = Sheet(ImportSheetUi.Choose(newProject = true, projectName = ""))

/** Reading the image (Meta, then OpenAI). */
@PreviewTest
@ChronovaScreenshots
@Composable
fun Reading() = Sheet(ImportSheetUi.Reading)

/** A chart without dates becomes a new project starting on a chosen day, with a note to check. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun PreviewNewProject() = Sheet(
    ImportSheetUi.Preview(
        ImportPreviewUi(
            newProject = true, projectName = "Project Timeline", projectStart = monday, projectEnd = monday.plusDays(48),
            relative = true, start = monday, rows = steps, uncertain = listOf("The chart has no year; weeks count from the start you pick."),
        ),
    ),
)

/** Adding to an open project: a task outside its dates arrives unticked; Apply explains a problem. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun PreviewAddToProject() = Sheet(
    ImportSheetUi.Preview(
        ImportPreviewUi(
            newProject = false, projectName = "Office move", projectStart = monday, projectEnd = monday.plusDays(20),
            relative = false, start = monday, rows = steps.take(3) + row("t4", "Release Design For Bid", 21, 27, fits = false),
            uncertain = emptyList(), problem = ImportPlanProblemUi.DoesNotFit("Release Design For Bid"),
        ),
    ),
)

/** Not a schedule: try another image. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun NotASchedule() = Sheet(ImportSheetUi.Problem(ImportProblemUi.NotASchedule))

/** Created, with Undo and Open project. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun Done() = Sheet(ImportSheetUi.Done(ImportDoneUi("p9", "Project Timeline", 9, newProject = true)))

/**
 * A whiteboard read with five things to check, on a phone-sized sheet: the warnings scroll with the
 * rows and the button stays on screen (it was pushed off a 720 × 1600 phone before).
 */
@PreviewTest
@ChronovaScreenshots
@Composable
fun PreviewManyWarnings() = Sheet(
    ImportSheetUi.Preview(
        ImportPreviewUi(
            newProject = false, projectName = "Cafe Opening", projectStart = monday, projectEnd = monday.plusDays(11),
            relative = false, start = monday,
            rows = listOf(
                row("t1", "DESIGN", 0, 1),
                row("t2", "BUILD", 2, 5, links = 1),
                row("t3", "EQUIPMENT INSTALL", 3, 3, links = 1),
                row("t4", "HIRE STAFF", 0, 2),
                row("t5", "TRAIN STAFF", 9, 9, links = 2),
                row("t6", "MENU TASTING", 10, 10),
                row("m1", "SOFT OPENING", 11, 11, links = 2, milestone = true),
            ),
            uncertain = listOf(
                "Year inferred as 2026 from the open project and today.",
                "BUILD and HIRE STAFF measurements differ from the seen bar ends; measurements used.",
                "EQUIPMENT INSTALL text date overrides its bar.",
                "TRAIN STAFF and MENU TASTING measurements appear shifted; seen dates used.",
                "SOFT OPENING milestone position falls outside the axis; seen date used.",
            ),
        ),
    ),
)

/** A one-day task with "10–1" written inside its bar shows the hours it will get; the others their days. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun PreviewHours() = Sheet(
    ImportSheetUi.Preview(
        ImportPreviewUi(
            newProject = false, projectName = "Cafe Opening", projectStart = monday, projectEnd = monday.plusDays(11),
            relative = false, start = monday,
            rows = listOf(
                row("t5", "TRAIN STAFF", 9, 9, links = 2),
                row("t6", "MENU TASTING", 10, 10).copy(startTime = LocalTime.of(10, 0), endTime = LocalTime.of(13, 0)),
                row("m1", "SOFT OPENING", 11, 11, links = 2, milestone = true),
            ),
            uncertain = emptyList(),
        ),
    ),
)
