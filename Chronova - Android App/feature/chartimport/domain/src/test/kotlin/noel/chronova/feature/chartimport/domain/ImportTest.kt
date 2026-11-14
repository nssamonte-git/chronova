package noel.chronova.feature.chartimport.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import noel.chronova.core.domain.ProjectProblem
import noel.chronova.core.domain.TaskProblem
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.core.model.Project
import noel.chronova.core.model.SchedulerTask
import noel.chronova.core.model.TaskColor
import noel.chronova.core.testing.FakeAuthRepository
import noel.chronova.core.testing.FakeNotificationRepository
import noel.chronova.core.testing.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val zone: ZoneId = ZoneId.of("Asia/Manila")

private fun task(ref: String, title: String, start: Int, end: Int, after: List<String> = emptyList(), milestone: Boolean = false, progress: Int = 0) =
    ImportedTask(ref, title, null, null, start, end, milestone, progress, after)

/** The test chart (docs/import-api.md): nine steps over seven weeks, relative days. */
private val chart = ImportedSchedule(
    ImportedSchedule.Result.OK,
    ImportedSchedule.Dates.RELATIVE,
    "Project Timeline",
    null,
    null,
    49,
    listOf(
        task("t1", "Initial Client Consult", 0, 6),
        task("t2", "Project Design Inquiry", 0, 13),
        task("t3", "Initial Design Concept", 14, 20, after = listOf("t2")),
        task("t4", "Release Design For Bid", 21, 27, after = listOf("t3")),
        task("t5", "2nd Design Concept", 28, 34),
        task("t6", "Sales Presentation & Proposals", 35, 41),
        task("t7", "Purchasing of Materials", 35, 41),
        task("t8", "Install", 42, 48),
        task("t9", "Review Finished Project", 42, 48),
    ),
    emptyList(),
)

private fun local(millis: Long): LocalDateTime = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

class ImportTest {
    private var ids = 0
    private val planner = PlanImport(zone) { "id${++ids}" }
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun aRelativeChartBecomesANewProjectFromTheChosenStart() {
        val rows = ImportPreview.rows(chart, ImportTarget.NewProject, monday)
        val (start, end) = ImportPreview.projectDays(chart, monday)
        assertEquals(monday to LocalDate.of(2027, 1, 3), start to end) // 49 days
        assertEquals(rows.map { it.ref }.toSet(), ImportPreview.defaultSelection(rows))

        val plan = planner.plan(chart, ImportTarget.NewProject, ImportChoices(ImportPreview.defaultSelection(rows), monday, "Project Timeline", start, end), null)
        val project = ((plan as PlannedImport.Ready).plan as ImportPlan.NewProject)
        assertEquals(Project("id1", "Project Timeline", monday, end), project.project)
        assertEquals(9, project.tasks.size)

        val inquiry = project.tasks[1]
        assertEquals("Project Design Inquiry", inquiry.title)
        assertEquals(LocalDateTime.of(2026, 11, 16, 0, 0), local(inquiry.startMillis))
        assertEquals(LocalDateTime.of(2026, 11, 30, 0, 0), local(inquiry.endMillis)) // through day 13, inclusive
        assertEquals(listOf(TaskColor.BLUE, TaskColor.GREEN, TaskColor.PURPLE, TaskColor.AMBER, TaskColor.BLUE), project.tasks.take(5).map { it.color })
        assertEquals((0..8).toList(), project.tasks.map { it.sortOrder })
        assertEquals(
            listOf("Project Design Inquiry" to "Initial Design Concept", "Initial Design Concept" to "Release Design For Bid"),
            project.links.map { link -> project.tasks.first { it.id == link.predecessorId }.title to project.tasks.first { it.id == link.successorId }.title },
        )
    }

    @Test
    fun aLinkToAnUntickedRowIsDropped() {
        val plan = planner.plan(chart, ImportTarget.NewProject, ImportChoices(setOf("t3", "t4"), monday, "P", monday, monday.plusDays(48)), null)
        val project = (plan as PlannedImport.Ready).plan as ImportPlan.NewProject
        assertEquals(listOf("Initial Design Concept", "Release Design For Bid"), project.tasks.map { it.title })
        assertEquals(1, project.links.size) // t3 → t4 stays; t2 → t3 went with t2
    }

    @Test
    fun calendarDatesAreKeptAndAMilestoneStandsAtNine() {
        val dated = ImportedSchedule(
            ImportedSchedule.Result.OK, ImportedSchedule.Dates.ABSOLUTE, "Launch", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 13), null,
            listOf(
                ImportedTask("t1", "Build", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6), null, null, false, 40, emptyList()),
                ImportedTask("m1", "Go live", LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 13), null, null, true, 0, listOf("t1")),
            ),
            listOf("The year was guessed."),
        )
        val (start, end) = ImportPreview.projectDays(dated, monday) // the chosen start doesn't move calendar dates
        assertEquals(LocalDate.of(2026, 3, 2) to LocalDate.of(2026, 3, 13), start to end)

        val plan = planner.plan(dated, ImportTarget.NewProject, ImportChoices(setOf("t1", "m1"), monday, " Launch ", start, end), null)
        val project = (plan as PlannedImport.Ready).plan as ImportPlan.NewProject
        assertEquals("Launch", project.project.name)
        assertEquals(40, project.tasks[0].progress)
        val golive = project.tasks[1]
        assertTrue(golive.isMilestone)
        assertEquals(LocalDateTime.of(2026, 3, 13, 9, 0), local(golive.startMillis))
        assertEquals(golive.startMillis, golive.endMillis)
    }

    /** The whiteboard: "MENU TASTING" drawn on Thu 26 Nov only, with "10–1" written inside its bar. */
    @Test
    fun aOneDayTaskTakesTheHoursWrittenOnTheChart() {
        val thursday = LocalDate.of(2026, 11, 26)
        fun oneDay(ref: String, start: LocalTime?, end: LocalTime?, last: LocalDate = thursday, milestone: Boolean = false) =
            ImportedTask(ref, ref, thursday, last, null, null, milestone, 0, emptyList(), start, end)
        val board = ImportedSchedule(
            ImportedSchedule.Result.OK, ImportedSchedule.Dates.ABSOLUTE, "Cafe Opening", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 27), null,
            listOf(
                oneDay("tasting", LocalTime.of(10, 0), LocalTime.of(13, 0)),
                oneDay("no hours", null, null),
                oneDay("two days", LocalTime.of(10, 0), LocalTime.of(13, 0), last = thursday.plusDays(1)),
                oneDay("too short", LocalTime.of(10, 0), LocalTime.of(10, 5)),
                oneDay("half", LocalTime.of(10, 0), null),
                oneDay("moment", LocalTime.of(10, 0), LocalTime.of(13, 0), milestone = true),
            ),
            emptyList(),
        )
        val rows = ImportPreview.rows(board, ImportTarget.NewProject, monday)
        // The preview says which rows have hours: only those that will be placed on them.
        assertEquals(
            listOf(LocalTime.of(10, 0) to LocalTime.of(13, 0)) + List(5) { null to null },
            rows.map { it.startTime to it.endTime },
        )

        val plan = planner.plan(board, ImportTarget.NewProject, ImportChoices(rows.map { it.ref }.toSet(), monday, "Cafe Opening", board.startDate!!, board.endDate!!), null)
        val tasks = ((plan as PlannedImport.Ready).plan as ImportPlan.NewProject).tasks.associateBy { it.title }
        fun span(title: String) = tasks.getValue(title).let { local(it.startMillis) to local(it.endMillis) }

        assertEquals(LocalDateTime.of(2026, 11, 26, 10, 0) to LocalDateTime.of(2026, 11, 26, 13, 0), span("tasting"))
        // Without usable hours a task keeps its whole days: never a task with no length.
        val wholeDay = LocalDateTime.of(2026, 11, 26, 0, 0) to LocalDateTime.of(2026, 11, 27, 0, 0)
        assertEquals(wholeDay, span("no hours"))
        assertEquals(wholeDay, span("too short"))
        assertEquals(wholeDay, span("half"))
        assertEquals(LocalDateTime.of(2026, 11, 26, 0, 0) to LocalDateTime.of(2026, 11, 28, 0, 0), span("two days"))
        assertEquals(LocalDateTime.of(2026, 11, 26, 9, 0) to LocalDateTime.of(2026, 11, 26, 9, 0), span("moment"))
        assertTrue(tasks.values.filterNot { it.isMilestone }.all { it.endMillis > it.startMillis })
    }

    @Test
    fun addingToAProjectUntickesWhatDoesNotFitAndFollowsItsTasks() = runTest {
        val project = Project("p1", "Office move", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 13)) // 4 weeks
        val repository = FakeProjectRepository(
            listOf(project),
            listOf(SchedulerTask("old", "p1", "Kickoff", 0, 1, color = TaskColor.BLUE, sortOrder = 4)),
        )
        val target = ImportTarget.Existing(project)
        val start = ImportPreview.defaultStart(target, monday)
        val rows = ImportPreview.rows(chart, target, start)
        assertEquals(listOf("t1", "t2", "t3", "t4"), ImportPreview.defaultSelection(rows).toList())
        assertFalse(rows.first { it.ref == "t5" }.fits)

        val existing = LoadImportTarget(repository)("p1")
        val plan = planner.plan(chart, target, ImportChoices(setOf("t1", "t2"), start), existing)
        val add = (plan as PlannedImport.Ready).plan as ImportPlan.AddToProject
        assertEquals(2, add.taskCount)
        val created = add.changes.filterIsInstance<noel.chronova.core.domain.schedule.ScheduleChange.CreateTask>().map { it.task }
        assertEquals(listOf(5, 6), created.map { it.sortOrder })
        assertEquals(listOf(TaskColor.GREEN, TaskColor.PURPLE), created.map { it.color })

        // Ticking a task outside the project is refused, with its title.
        val outside = planner.plan(chart, target, ImportChoices(setOf("t1", "t9"), start), existing)
        assertEquals(PlannedImport.Invalid(ImportProblem.TaskDoesNotFit("Review Finished Project", TaskProblem.OutsideProject)), outside)
    }

    @Test
    fun nothingTickedOrABadNewProjectIsRefused() {
        assertEquals(PlannedImport.Invalid(ImportProblem.NothingSelected), planner.plan(chart, ImportTarget.NewProject, ImportChoices(emptySet(), monday, "P", monday, monday), null))
        assertEquals(
            PlannedImport.Invalid(ImportProblem.BadProject(ProjectProblem.BlankName)),
            planner.plan(chart, ImportTarget.NewProject, ImportChoices(setOf("t1"), monday, "  ", monday, monday.plusDays(48)), null),
        )
        // Shrinking the new project's days below a task's makes that task not fit.
        val short = planner.plan(chart, ImportTarget.NewProject, ImportChoices(setOf("t1", "t8"), monday, "P", monday, monday.plusDays(13)), null)
        assertEquals(PlannedImport.Invalid(ImportProblem.TaskDoesNotFit("Install", TaskProblem.OutsideProject)), short)
    }

    @Test
    fun applyingWritesOnceRecordsItAndUndoTakesItBack() = runTest {
        val repository = FakeProjectRepository()
        val notifications = FakeNotificationRepository()
        val apply = ApplyImport(repository, ApplyScheduleChanges(repository, zone), notifications)
        val undo = UndoImport(repository, ApplyScheduleChanges(repository, zone))
        val end = monday.plusDays(48)

        val created = apply((planner.plan(chart, ImportTarget.NewProject, ImportChoices(setOf("t2", "t3"), monday, "Timeline", monday, end), null) as PlannedImport.Ready).plan)
        assertEquals(ImportOutcome.Created("id1", 2), created)
        assertEquals(2, repository.tasks.value.size)
        assertEquals(1, repository.dependencies.value.size)
        val note = notifications.added.single().notification
        assertEquals(NotificationKind.IMPORTED to mapOf("count" to "2"), note.kind to note.args)
        assertEquals("Timeline", note.projectName)
        assertFalse(note.canUndo) // a new project is undone from the snackbar (deleting it)

        assertTrue(undo(created))
        assertEquals(emptyList<Project>(), repository.projects.value)

        // Adding to a project keeps the Undo on the notifications page too.
        val project = Project("p1", "Office move", monday, end)
        repository.projects.value = listOf(project)
        val target = ImportTarget.Existing(project)
        val added = apply((planner.plan(chart, target, ImportChoices(setOf("t1"), monday), LoadImportTarget(repository)("p1")) as PlannedImport.Ready).plan)
        assertTrue(added is ImportOutcome.Added)
        assertTrue(notifications.added.last().notification.canUndo)
        assertEquals(1, repository.tasks.value.size)
        assertTrue(undo(added))
        assertEquals(emptyList<SchedulerTask>(), repository.tasks.value)
        assertFalse("already undone: refused as stale", undo(added))
    }

    @Test
    fun readingNeedsASessionAndSendsTheZoneTodayAndTheProject() = runTest {
        var sent: List<Any?>? = null
        val api = object : ImportApi {
            override suspend fun read(accessToken: String, image: ByteArray, timeZone: String, today: LocalDate, project: Project?): ImportedSchedule {
                sent = listOf(accessToken, image.size, timeZone, today, project?.id)
                return chart
            }
        }
        val prepared = mutableListOf<String>()
        val images = object : ChartImages {
            override suspend fun newPhotoUri() = "content://photo"
            override suspend fun prepare(uri: String): ByteArray = ByteArray(3).also { prepared += uri }
            override suspend fun clear() = Unit
        }
        val auth = FakeAuthRepository()
        val read = ReadChart(api, images, auth::accessToken, { ZoneId.of("Z") }, { monday })
        val project = Project("p1", "Office move", monday, monday)

        assertEquals(chart, read("content://picked", ImportTarget.Existing(project)))
        assertEquals(listOf("token-1", 3, "UTC", monday, "p1"), sent)
        assertEquals(listOf("content://picked"), prepared)

        auth.token = null
        val error = runCatching { read("content://picked", ImportTarget.NewProject) }.exceptionOrNull()
        assertEquals("not prepared without a session", 1, prepared.size)
        assertEquals(ImportApiException.Kind.UNAUTHORIZED, (error as ImportApiException).kind)
        assertNull(LoadImportTarget(FakeProjectRepository())("gone"))
    }
}
