package noel.chronova.feature.chartimport.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate
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
// In a Surface, as the bottom sheet gives it its colours on the phone.
private fun Sheet(state: ImportSheetUi) = ChronovaTheme { Surface { ImportSheetContent(state, ImportActions()) } }

/** As it opens from "New project": take a photo or choose an image. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ImportChoose() = Sheet(ImportSheetUi.Choose(newProject = true, projectName = ""))

/** Reading the image (Meta, then OpenAI). */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ImportReading() = Sheet(ImportSheetUi.Reading)

/** A chart without dates becomes a new project starting on a chosen day, with a note to check. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ImportPreviewNewProject() = Sheet(
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
fun ImportPreviewAddToProject() = Sheet(
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
fun ImportNotASchedule() = Sheet(ImportSheetUi.Problem(ImportProblemUi.NotASchedule))

/** Created, with Undo and Open project. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun ImportDone() = Sheet(ImportSheetUi.Done(ImportDoneUi("p9", "Project Timeline", 9, newProject = true)))
