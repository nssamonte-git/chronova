package noel.chronova.feature.export.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.withClip
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import noel.chronova.core.domain.schedule.ScheduleState
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import noel.chronova.feature.export.domain.PdfChartWriter

/**
 * The whole project as a printable Gantt chart: A4 landscape pages, a name column and a
 * time axis scaled so the project's full date range fits the page width (day, week or
 * month ticks by length), bars in the task colours with the done part darkened, milestone
 * diamonds, finish-to-start arrows (red when broken), and rows continuing over as many
 * pages as needed, each with the header and axis. Always light, whatever the app's theme.
 */
class GanttPdfWriter(private val locale: Locale = Locale.getDefault()) : PdfChartWriter {

    override fun write(schedule: ScheduleState, zone: ZoneId, exportedAtMillis: Long): ByteArray {
        val tasks = schedule.tasks.sortedBy { it.sortOrder }
        val window = schedule.project.window(zone)
        val pages = if (tasks.isEmpty()) listOf(emptyList()) else tasks.chunked(ROWS_PER_PAGE)
        val axis = Axis(schedule.project.startDate, schedule.project.endDate, window.startMillis, window.endMillis, zone)
        val rowOf = tasks.withIndex().associate { (i, task) -> task.id to i }

        val document = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, rows ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create())
                val canvas = page.canvas
                header(canvas, schedule, tasks.size, zone, exportedAtMillis, continued = pageIndex > 0)
                axis.draw(canvas)
                val firstRow = pageIndex * ROWS_PER_PAGE
                rows.forEachIndexed { i, task -> row(canvas, task, i, axis) }
                if (rows.isEmpty()) emptyNote(canvas)
                arrows(canvas, schedule.links, tasks, rowOf, firstRow, rows.size, axis)
                footer(canvas, pageIndex + 1, pages.size)
                document.finishPage(page)
            }
            return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun header(canvas: Canvas, schedule: ScheduleState, taskCount: Int, zone: ZoneId, exportedAt: Long, continued: Boolean) {
        val project = schedule.project
        val title = if (continued) "${project.name} (continued)" else project.name
        canvas.drawText(ellipsize(title, TITLE, CONTENT_RIGHT - MARGIN), MARGIN, MARGIN + 18f, TITLE)
        val dates = "${longDay.format(project.startDate)} – ${longDay.format(project.endDate)}"
        val exported = longDay.format(Instant.ofEpochMilli(exportedAt).atZone(zone).toLocalDate())
        val tasks = if (taskCount == 1) "1 task" else "$taskCount tasks"
        canvas.drawText("$dates  ·  $tasks  ·  Exported $exported", MARGIN, MARGIN + 36f, SUBTLE)
    }

    private fun row(canvas: Canvas, task: SchedulerTask, index: Int, axis: Axis) {
        val top = ROWS_TOP + index * ROW_HEIGHT
        val middle = top + ROW_HEIGHT / 2
        canvas.drawLine(MARGIN, top + ROW_HEIGHT, CONTENT_RIGHT, top + ROW_HEIGHT, GRID)
        // Name column: a check once done, the title, and the progress when under way.
        val prefix = if (task.isDone) "✓ " else ""
        val suffix = if (task.progress in 1 until SchedulerTask.DONE) "  ${task.progress}%" else ""
        val name = ellipsize(prefix + task.title, TEXT, NAME_WIDTH - 8f - TEXT.measureText(suffix)) + suffix
        canvas.drawText(name, MARGIN, middle + 3.5f, TEXT)

        val colour = colourOf(task.color)
        val start = axis.x(task.startMillis)
        if (task.isMilestone) {
            val diamond = Path().apply {
                moveTo(start, middle - DIAMOND)
                lineTo(start + DIAMOND, middle)
                lineTo(start, middle + DIAMOND)
                lineTo(start - DIAMOND, middle)
                close()
            }
            canvas.drawPath(diamond, fill(colour))
            if (task.isDone) canvas.drawPath(diamond, fill(DONE_SHADE))
            return
        }
        val end = maxOf(axis.x(task.endMillis), start + MIN_BAR)
        val bar = RectF(start, middle - BAR_HEIGHT / 2, end, middle + BAR_HEIGHT / 2)
        canvas.drawRoundRect(bar, BAR_RADIUS, BAR_RADIUS, fill(colour))
        if (task.progress > 0) {
            val done = RectF(bar.left, bar.top, bar.left + bar.width() * task.progress / SchedulerTask.DONE, bar.bottom)
            canvas.withClip(done) { drawRoundRect(bar, BAR_RADIUS, BAR_RADIUS, fill(DONE_SHADE)) }
        }
    }

    /** Arrows from a predecessor's end to its successor's start, for links whose successor is on this page. */
    private fun arrows(
        canvas: Canvas,
        links: List<TaskDependency>,
        tasks: List<SchedulerTask>,
        rowOf: Map<String, Int>,
        firstRow: Int,
        rowCount: Int,
        axis: Axis,
    ) {
        for (link in links) {
            val from = rowOf[link.predecessorId] ?: continue
            val to = rowOf[link.successorId] ?: continue
            if (to - firstRow !in 0 until rowCount) continue
            val predecessor = tasks[from]
            val successor = tasks[to]
            val paint = if (link.isViolated(predecessor, successor)) ARROW_BROKEN else ARROW
            val endY = ROWS_TOP + (to - firstRow) * ROW_HEIGHT + ROW_HEIGHT / 2
            // A predecessor on an earlier page: the arrow starts at the top of this page's rows.
            val startY = if (from >= firstRow) ROWS_TOP + (from - firstRow) * ROW_HEIGHT + ROW_HEIGHT / 2 else ROWS_TOP
            val startX = axis.x(predecessor.endMillis)
            val endX = axis.x(successor.startMillis) - if (successor.isMilestone) DIAMOND else 0f
            val bend = startX + 4f
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(bend, startY)
                lineTo(bend, endY)
                lineTo(endX, endY)
            }
            canvas.drawPath(path, paint)
            canvas.drawPath(arrowHead(endX, endY), fill(paint.color))
        }
    }

    private fun arrowHead(x: Float, y: Float) = Path().apply {
        moveTo(x, y)
        lineTo(x - 4f, y - 2.5f)
        lineTo(x - 4f, y + 2.5f)
        close()
    }

    private fun emptyNote(canvas: Canvas) {
        canvas.drawText("No tasks yet.", MARGIN, ROWS_TOP + ROW_HEIGHT, SUBTLE)
    }

    private fun footer(canvas: Canvas, page: Int, pages: Int) {
        val text = "Chronova  ·  page $page of $pages"
        canvas.drawText(text, CONTENT_RIGHT - SMALL.measureText(text), PAGE_HEIGHT - MARGIN / 2, SMALL)
    }

    /** The time axis: the project's window spread over the chart width, with ticks by its length. */
    private inner class Axis(
        private val firstDay: LocalDate,
        private val lastDay: LocalDate,
        private val startMillis: Long,
        private val endMillis: Long,
        private val zone: ZoneId,
    ) {
        fun x(millis: Long): Float =
            CHART_LEFT + (millis - startMillis).toFloat() / (endMillis - startMillis) * (CONTENT_RIGHT - CHART_LEFT)

        fun draw(canvas: Canvas) {
            val days = ChronoUnit.DAYS.between(firstDay, lastDay) + 1
            val ticks: List<LocalDate> = when {
                days <= DAY_TICKS_UP_TO -> generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList()
                days <= WEEK_TICKS_UP_TO -> generateSequence(firstDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))) { it.plusWeeks(1) }
                    .takeWhile { !it.isAfter(lastDay) }.toList()
                else -> generateSequence(firstDay.withDayOfMonth(1).let { if (it.isBefore(firstDay)) it.plusMonths(1) else it }) { it.plusMonths(1) }
                    .takeWhile { !it.isAfter(lastDay) }.toList()
            }
            val labelFormat = if (days <= WEEK_TICKS_UP_TO) shortDay else month
            // Only as many labels as fit; the lines are drawn for every tick.
            val step = maxOf(1, (ticks.size * LABEL_WIDTH / (CONTENT_RIGHT - CHART_LEFT)).toInt() + 1)
            canvas.drawLine(MARGIN, ROWS_TOP, CONTENT_RIGHT, ROWS_TOP, AXIS)
            canvas.drawLine(CHART_LEFT, AXIS_TOP, CHART_LEFT, PAGE_HEIGHT - MARGIN - FOOTER, GRID)
            ticks.forEachIndexed { i, day ->
                val x = x(day.atStartOfDay(zone).toInstant().toEpochMilli())
                canvas.drawLine(x, ROWS_TOP - 4f, x, PAGE_HEIGHT - MARGIN - FOOTER, GRID)
                if (i % step == 0) canvas.drawText(labelFormat.format(day), x + 2f, ROWS_TOP - 7f, SMALL)
            }
            canvas.drawText("Task", MARGIN, ROWS_TOP - 7f, SMALL)
        }
    }

    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        val fits = paint.breakText(text, true, width - paint.measureText("…"), null)
        return text.take(fits).trimEnd() + "…"
    }

    private val longDay = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val shortDay = DateTimeFormatter.ofPattern("d MMM", locale)
    private val month = DateTimeFormatter.ofPattern("MMM yyyy", locale)

    private companion object {
        // A4 landscape in PostScript points.
        const val PAGE_WIDTH = 842
        const val PAGE_HEIGHT = 595
        const val MARGIN = 32f
        const val CONTENT_RIGHT = PAGE_WIDTH - MARGIN
        const val NAME_WIDTH = 170f
        const val CHART_LEFT = MARGIN + NAME_WIDTH
        const val AXIS_TOP = 84f
        const val ROWS_TOP = 104f
        const val ROW_HEIGHT = 22f
        const val FOOTER = 14f
        val ROWS_PER_PAGE = ((PAGE_HEIGHT - MARGIN - FOOTER - ROWS_TOP) / ROW_HEIGHT).toInt()
        const val BAR_HEIGHT = 12f
        const val BAR_RADIUS = 3f
        const val MIN_BAR = 2f
        const val DIAMOND = 5.5f
        const val LABEL_WIDTH = 46f
        const val DAY_TICKS_UP_TO = 45L
        const val WEEK_TICKS_UP_TO = 200L

        // The app's light palette.
        val INK = Color.rgb(0x0F, 0x17, 0x2A)
        val MUTED = Color.rgb(0x47, 0x55, 0x69)
        val LINE = Color.rgb(0xE2, 0xE8, 0xF0)
        val DONE_SHADE = Color.argb(0x38, 0, 0, 0)

        val TITLE = text(18f, INK, bold = true)
        val TEXT = text(9.5f, INK)
        val SUBTLE = text(10f, MUTED)
        val SMALL = text(8f, MUTED)
        val GRID = stroke(LINE, 0.6f)
        val AXIS = stroke(Color.rgb(0xCB, 0xD5, 0xE1), 0.8f)
        val ARROW = stroke(Color.rgb(0x64, 0x74, 0x8B), 0.8f)
        val ARROW_BROKEN = stroke(Color.rgb(0xDC, 0x26, 0x26), 0.8f)

        fun colourOf(color: TaskColor): Int = when (color) {
            TaskColor.BLUE -> Color.rgb(0x3B, 0x82, 0xF6)
            TaskColor.GREEN -> Color.rgb(0x10, 0xB9, 0x81)
            TaskColor.PURPLE -> Color.rgb(0x8B, 0x5C, 0xF6)
            TaskColor.AMBER -> Color.rgb(0xF5, 0x9E, 0x0B)
        }

        fun text(size: Float, colour: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = colour
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

        fun stroke(colour: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = colour
            strokeWidth = width
        }

        fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colour
        }
    }
}
