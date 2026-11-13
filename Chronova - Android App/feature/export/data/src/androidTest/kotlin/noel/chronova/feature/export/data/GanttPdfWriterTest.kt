package noel.chronova.feature.export.data

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import noel.chronova.core.domain.schedule.ScheduleState
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.model.TaskDependency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real PDFs from [GanttPdfWriter], opened again with Android's own PDF reader. */
@RunWith(AndroidJUnit4::class)
class GanttPdfWriterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneOffset.UTC
    private val writer = GanttPdfWriter(Locale.UK)

    private fun project(days: Long) = Project("p", "Fit-out", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2).plusDays(days - 1))

    private fun task(i: Int, project: Project): SchedulerTask {
        val start = project.startDate.plusDays(i.toLong() % 5).atTime(9, 0).toInstant(zone).toEpochMilli()
        return SchedulerTask("t$i", "p", "Task number $i with a long enough title to be cut", start, start + 3_600_000L * (1 + i % 4), TaskColor.entries[i % 4], i, progress = (i * 10) % 110)
    }

    /** How many pages Android's reader finds in [bytes]. */
    private fun pages(bytes: ByteArray): Int {
        assertTrue(String(bytes.copyOfRange(0, 5), Charsets.US_ASCII) == "%PDF-")
        val file = File(context.cacheDir, "test.pdf").apply { writeBytes(bytes) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
    }

    @Test
    fun anEmptyProjectIsOnePage() {
        assertEquals(1, pages(writer.write(ScheduleState(project(7), emptyList(), emptyList()), zone, 0L)))
    }

    @Test
    fun aMilestoneAndALinkFitOnOnePage() {
        val p = project(7)
        val first = task(0, p)
        val milestone = SchedulerTask("m", "p", "Launch", first.endMillis, first.endMillis, TaskColor.AMBER, 1, progress = 100, isMilestone = true)
        val schedule = ScheduleState(p, listOf(first, milestone), listOf(TaskDependency("l", "p", first.id, "m")))
        assertEquals(1, pages(writer.write(schedule, zone, 0L)))
    }

    @Test
    fun manyTasksContinueOverSeveralPagesForAnyLength() {
        for (days in listOf(3L, 30L, 120L, 366L)) {
            val p = project(days)
            val tasks = (0 until 60).map { task(it, p) }
            val links = (1 until 60).map { TaskDependency("l$it", "p", "t${it - 1}", "t$it") }
            val count = pages(writer.write(ScheduleState(p, tasks, links), zone, 0L))
            assertTrue("$days days: $count pages", count in 3..4)
        }
    }
}
