package noel.chronova.feature.projects.presentation

import noel.chronova.core.domain.ProjectProblem
import java.time.ZoneId
import noel.chronova.core.model.ProjectSummary
import noel.chronova.core.model.TaskOutline

// Domain to screen: the list and the editor never see the domain types.

/**
 * [outlines] are this project's tasks in row order. They become the card's small chart, placed
 * within the project's days in [zone]: one row per task while there are at most
 * [MAX_CARD_LANES], otherwise every task packed into that many rows ([packIntoLanes]).
 */
internal fun ProjectSummary.toUi(outlines: List<TaskOutline> = emptyList(), zone: ZoneId = ZoneId.systemDefault()): ProjectRowUi {
    val window = project.window(zone)
    val length = (window.endMillis - window.startMillis).toFloat()
    fun share(millis: Long) = ((millis - window.startMillis) / length).coerceIn(0f, 1f)
    val bars = outlines.map { outline ->
        var start = share(outline.startMillis)
        var end = share(outline.endMillis)
        if (outline.isMilestone) {
            // A dot at its moment; the length is only the room it takes in its row.
            end = (start + MIN_BAR_SHARE).coerceAtMost(1f)
        } else if (end - start < MIN_BAR_SHARE) {
            // An hour in a month-long project would be a hair: give it a length that can be seen.
            end = start + MIN_BAR_SHARE
            if (end > 1f) {
                end = 1f
                start = 1f - MIN_BAR_SHARE
            }
        }
        ProjectBarUi(start, end, outline.color, outline.isMilestone)
    }
    return ProjectRowUi(
        id = project.id,
        name = project.name,
        startDate = project.startDate,
        endDate = project.endDate,
        taskCount = taskCount,
        doneCount = doneCount,
        lanes = if (bars.size <= MAX_CARD_LANES) bars.map { listOf(it) } else packIntoLanes(bars),
    )
}

/**
 * Every bar in at most [maxLanes] rows, earliest first: a bar goes in the first row that is
 * free at its start, in a new row while there is room for one, and otherwise in the row that
 * frees up soonest (it then overlaps the bar before it; nothing is left out). The rows no
 * longer stand for tasks: together they show where the project is busy.
 */
internal fun packIntoLanes(bars: List<ProjectBarUi>, maxLanes: Int = MAX_CARD_LANES): List<List<ProjectBarUi>> {
    val lanes = ArrayList<ArrayList<ProjectBarUi>>()
    val ends = ArrayList<Float>()
    for (bar in bars.sortedWith(compareBy({ it.start }, { it.end }))) {
        // A hair of tolerance: shares are rounded, and "ends where the next begins" must count as free.
        var lane = ends.indexOfFirst { it <= bar.start + LANE_TOLERANCE }
        if (lane < 0) {
            if (lanes.size < maxLanes) {
                lanes += ArrayList<ProjectBarUi>()
                ends += 0f
                lane = lanes.lastIndex
            } else {
                lane = ends.indices.minBy { ends[it] }
            }
        }
        lanes[lane] += bar
        ends[lane] = maxOf(ends[lane], bar.end)
    }
    return lanes
}

/** How many rows a card's small chart has at most: enough to see the project's shape, short enough for a list. */
internal const val MAX_CARD_LANES = 5

/** How far two bars may overlap and still share a row: far less than a pixel on any card. */
private const val LANE_TOLERANCE = 0.0005f

/** The shortest a bar is drawn, as a share of the card's width. */
internal const val MIN_BAR_SHARE = 0.03f

internal fun ProjectProblem.toUi(): ProjectProblemUi = when (this) {
    ProjectProblem.BlankName -> ProjectProblemUi.BlankName
    ProjectProblem.EndBeforeStart -> ProjectProblemUi.EndBeforeStart
    is ProjectProblem.TooLong -> ProjectProblemUi.TooLong(maxDays)
    is ProjectProblem.ExcludesTasks -> ProjectProblemUi.ExcludesTasks(count)
}

internal fun ProjectRowUi.toSource() = ProjectSourceUi(id, name, startDate, endDate)
