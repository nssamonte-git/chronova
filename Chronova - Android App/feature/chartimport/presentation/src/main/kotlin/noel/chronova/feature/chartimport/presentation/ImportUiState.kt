package noel.chronova.feature.chartimport.presentation

import java.time.LocalDate

/** The import sheet, step by step. */
sealed interface ImportSheetUi {
    /** Take a photo or choose an image. [newProject]: the sheet makes a new project, else adds to [projectName]. */
    data class Choose(val newProject: Boolean, val projectName: String) : ImportSheetUi

    /** The image is on its way and being read (Meta, then OpenAI): it takes a while. */
    data object Reading : ImportSheetUi

    data class Preview(val preview: ImportPreviewUi) : ImportSheetUi

    /** Nothing to preview: what went wrong, and a way to try another image. */
    data class Problem(val problem: ImportProblemUi) : ImportSheetUi

    /** Applied: what was added, with Undo while it is still offered. */
    data class Done(val done: ImportDoneUi) : ImportSheetUi
}

data class ImportPreviewUi(
    /** A new project from the chart; otherwise its tasks go into [projectName]. */
    val newProject: Boolean,
    val projectName: String,
    /** A new project's days (shown, editable). */
    val projectStart: LocalDate,
    val projectEnd: LocalDate,
    /** The chart had no calendar dates: its days count from [start], which the user picks. */
    val relative: Boolean,
    val start: LocalDate,
    val rows: List<ImportRowUi>,
    /** What the reading guessed, for the user to check. */
    val uncertain: List<String>,
    val applying: Boolean = false,
    val problem: ImportPlanProblemUi? = null,
) {
    val selectedCount: Int get() = rows.count { it.selected }
    val milestoneCount: Int get() = rows.count { it.milestone }
    val linkCount: Int get() = rows.sumOf { it.links }
}

data class ImportRowUi(
    val ref: String,
    val title: String,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val milestone: Boolean,
    val progress: Int,
    /** Links to this task from tasks before it. */
    val links: Int,
    /** False when the open project's dates don't hold it. */
    val fits: Boolean,
    val selected: Boolean,
)

enum class ImportProblemUi {
    Offline,
    NotASchedule,
    Unreadable,
    BadImage,
    NoCamera,
    TooMany,
    SwitchedOff,
    SignInAgain,
    ProjectGone,
    Unavailable,
}

/** Why Apply did nothing. */
sealed interface ImportPlanProblemUi {
    data object NothingSelected : ImportPlanProblemUi
    data object BlankName : ImportPlanProblemUi
    data object EndBeforeStart : ImportPlanProblemUi
    data class TooLong(val maxDays: Int) : ImportPlanProblemUi
    data class DoesNotFit(val title: String) : ImportPlanProblemUi

    /** The project changed or went while the preview was open. */
    data object ProjectChanged : ImportPlanProblemUi
}

data class ImportDoneUi(
    val projectId: String,
    val projectName: String,
    val taskCount: Int,
    val newProject: Boolean,
    /** Null until Undo is tapped; then whether it worked (it doesn't once the tasks were edited). */
    val undone: Boolean? = null,
)
