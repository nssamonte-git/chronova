package noel.chronova.app.presentation.voice

import java.io.File
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import noel.chronova.app.domain.auth.AuthOutcome
import noel.chronova.app.domain.auth.AuthRepository
import noel.chronova.app.domain.auth.AuthState
import noel.chronova.app.data.remote.DecisionAnswer
import noel.chronova.app.data.remote.ExplainRequest
import noel.chronova.app.data.remote.ExplainStage
import noel.chronova.app.data.remote.SpokenAnswer
import noel.chronova.app.data.remote.Transcript
import noel.chronova.app.data.remote.VoiceApi
import noel.chronova.app.data.remote.VoiceApiException
import noel.chronova.app.data.remote.VoiceDecision
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.notifications.NotificationKind
import noel.chronova.app.domain.schedule.CommandProblem
import noel.chronova.app.domain.schedule.ScheduleChange
import noel.chronova.app.domain.voice.ExplainedChange
import noel.chronova.app.domain.voice.Proposal
import noel.chronova.app.domain.voice.ProposedOperation
import noel.chronova.app.domain.voice.ScheduleWarning
import noel.chronova.app.domain.voice.VoiceContext
import noel.chronova.app.testing.FakeNotificationRepository
import noel.chronova.app.testing.FakeProjectRepository
import noel.chronova.app.voice.AssistantSettings
import noel.chronova.app.voice.RecorderUnavailable
import noel.chronova.app.voice.Recording
import noel.chronova.app.voice.SpeechPlayer
import noel.chronova.app.voice.VoiceRecorder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeAuth(initial: AuthState) : AuthRepository {
    override val state = MutableStateFlow(initial)
    override suspend fun refresh() = Unit
    override suspend fun signUp(email: String, password: String) = AuthOutcome.Done
    override suspend fun confirmSignUp(email: String, code: String) = AuthOutcome.Done
    override suspend fun resendCode(email: String) = AuthOutcome.Done
    override suspend fun signIn(email: String, password: String, remember: Boolean) = AuthOutcome.Done
    override suspend fun requestPasswordReset(email: String) = AuthOutcome.Done
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = AuthOutcome.Done
    override suspend fun signOut() = Unit
    override suspend fun accessToken(): String? = if (state.value is AuthState.SignedIn) "token" else null
    var ended = false
    override suspend fun endSession() {
        ended = true
    }
}

/** Hears [heard] in turn; answers each request with [answer]; records everything said. */
private class FakeVoiceApi : VoiceApi {
    val heard = ArrayDeque<String>()
    val requests = mutableListOf<String>()
    val explained = mutableListOf<ExplainRequest>()
    var answer: (String, VoiceContext) -> Proposal = { _, c -> Proposal(c.revision, "unsupported", emptyList(), null, null) }
    var onTranscribe: () -> Unit = {}
    var onExplain: (ExplainRequest) -> Unit = {}
    var transcribeFailure: VoiceApiException.Kind? = null

    val keyterms = mutableListOf<List<String>>()

    override suspend fun transcribe(accessToken: String, audio: File, durationMs: Long, language: String?, keyterms: List<String>): Transcript {
        this.keyterms += keyterms
        onTranscribe()
        transcribeFailure?.let { throw VoiceApiException(it) }
        return Transcript(heard.removeFirstOrNull() ?: "", "en")
    }

    override suspend fun interpret(accessToken: String, transcript: String, context: VoiceContext): Proposal {
        requests += transcript
        return answer(transcript, context)
    }

    /** What /resolve was asked: the operations planned and the clashes. */
    val resolved = mutableListOf<Pair<List<ProposedOperation>, List<ScheduleWarning>>>()
    var suggest: (VoiceContext) -> Proposal = { c -> Proposal(c.revision, "unsupported", emptyList(), null, "none") }
    var resolveFailure: VoiceApiException.Kind? = null

    override suspend fun resolve(
        accessToken: String,
        transcript: String,
        context: VoiceContext,
        planned: List<ProposedOperation>,
        clashes: List<ScheduleWarning>,
    ): Proposal {
        resolved += planned to clashes
        resolveFailure?.let { throw VoiceApiException(it) }
        return suggest(context)
    }

    val decisions = mutableListOf<VoiceDecision>()
    var decideFailure: VoiceApiException.Kind? = null

    override suspend fun decide(accessToken: String, decision: VoiceDecision) {
        decideFailure?.let { throw VoiceApiException(it) }
        decisions += decision
    }

    override suspend fun explain(accessToken: String, request: ExplainRequest): SpokenAnswer {
        explained += request
        onExplain(request)
        val text = when (request) {
            is ExplainRequest.Question -> request.text
            is ExplainRequest.Changes -> "${request.stage} of ${request.changes.size}"
        }
        return SpokenAnswer(text, byteArrayOf(1), "audio/mpeg")
    }

    val said: List<String> get() = explained.filterIsInstance<ExplainRequest.Question>().map { it.text }
}

/** Each start() either "speaks" (loud, then quiet) or stays silent, from [turns]; silent once they run out. */
private class FakeRecorder : VoiceRecorder {
    /** Each turn's speaking level: 0 is silence; 8,000 is someone talking to the phone. */
    val turns = ArrayDeque<Int>()
    var unavailable = false
    var recording = false
    var starts = 0
    private var level = 0
    private var samples = 0

    override fun start() {
        if (unavailable) throw RecorderUnavailable(IllegalStateException("busy"))
        starts++
        recording = true
        level = turns.removeFirstOrNull() ?: 0
        samples = 0
    }

    override fun amplitude(): Int {
        samples++
        return if (level > 0 && samples in 4..12) level else 300
    }

    override fun stop(): Recording? {
        recording = false
        return Recording(File.createTempFile("command", ".m4a"), 2_000)
    }

    override fun cancel() {
        recording = false
    }
}

private class FakePlayer : SpeechPlayer {
    override val playing = MutableStateFlow(false)
    val played = mutableListOf<ByteArray>()

    /** When set, each clip "plays" until the test scheduler has run what was waiting, like a real clip. */
    var holdIn: CoroutineScope? = null

    /** When true, each clip "plays" until stop() (like a long clip the user cuts short). */
    var untilStopped = false

    override fun play(audio: ByteArray) {
        played += audio // otherwise finishes at once
        if (untilStopped) {
            playing.value = true
            return
        }
        holdIn?.let { scope ->
            playing.value = true
            scope.launch {
                yield()
                playing.value = false
            }
        }
    }

    override fun stop() {
        playing.value = false
    }

    override fun release() = Unit
}

private class FakeSettings : AssistantSettings {
    override var noticeAccepted = false
    val introducedProjects = mutableSetOf<String>()
    override fun introduced(projectId: String) = projectId in introducedProjects
    override fun markIntroduced(projectId: String) {
        introducedProjects += projectId
    }
}

private object FakePhrases : AssistantPhrases {
    override fun greeting() = "hello"
    override fun introduction() = "intro"
    override fun applyAsAsked() = "as asked?"
    override fun wasThatAYes() = "was that a yes?"
    override fun nothingHeard() = "say again"
    override fun goodbye() = "goodbye"
    override fun nothingChanged() = "nothing changed"
    override fun notUnderstood() = "not understood"
    override fun unsupported(reason: String?) = reason ?: "unsupported"
    override fun nothingToChange() = "nothing to change"
    override fun scheduleChanged() = "schedule changed"
    override fun microphoneUnavailable() = "mic busy"
    override fun failure(kind: VoiceApiException.Kind) = "failure $kind"
    override fun cannotDo(problem: CommandProblem, titles: Map<String, String>, projectEndMillis: Long) = "cannot do"
}

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val zone = ZoneOffset.UTC
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone))
    private val api = FakeVoiceApi()
    private val auth = FakeAuth(AuthState.SignedIn("u1", "ana@example.com"))
    private val online = MutableStateFlow(true)
    private val recorder = FakeRecorder()
    private val player = FakePlayer()
    private val settings = FakeSettings()
    private val notifications = FakeNotificationRepository()
    private lateinit var viewModel: AssistantViewModel

    private val review get() = repository.tasks.value.first { it.id == "review" }
    private val originalReview = SampleTasks.tasks(zone).first { it.id == "review" }

    private fun shift(hours: Long) = { _: String, c: VoiceContext ->
        Proposal(c.revision, "operations", listOf(ProposedOperation("shift_task", taskId = "review", amount = hours, unit = "hours")), null, null)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = AssistantViewModel(
            SampleTasks.PROJECT_ID, repository, api, auth, online, recorder, player, settings, FakePhrases, zone,
            clock = { 0L }, newId = { "new" }, language = { "en" },
            notifications = notifications,
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModelFor(projectId: String) = AssistantViewModel(
        projectId, repository, api, auth, online, recorder, player, settings, FakePhrases, zone,
        clock = { 0L }, newId = { "new" }, language = { "en" },
        notifications = notifications,
    )

    @Test
    fun aProjectsFirstOpenIntroducesTheAssistantOnceWithoutListening() = runTest(dispatcher) {
        runCurrent() // availability is worked out
        viewModel.introduceIfFirstVisit()
        advanceUntilIdle()

        assertEquals(listOf("intro"), api.said)
        assertEquals(0, recorder.starts) // nothing recorded
        assertTrue(api.requests.isEmpty()) // nothing interpreted
        assertFalse(viewModel.state.value.open) // closes by itself
        assertTrue(settings.introduced(SampleTasks.PROJECT_ID))

        viewModel.introduceIfFirstVisit()
        val again = viewModelFor(SampleTasks.PROJECT_ID) // the project opened another time
        runCurrent()
        again.introduceIfFirstVisit()
        advanceUntilIdle()
        assertEquals(listOf("intro"), api.said)

        val other = viewModelFor("another-project")
        runCurrent()
        other.introduceIfFirstVisit()
        advanceUntilIdle()
        assertEquals(listOf("intro", "intro"), api.said)
    }

    @Test
    fun offlineTheIntroductionWaitsForALaterVisit() = runTest(dispatcher) {
        online.value = false
        runCurrent()
        viewModel.introduceIfFirstVisit()
        advanceUntilIdle()
        assertTrue(api.said.isEmpty())
        assertFalse(settings.introduced(SampleTasks.PROJECT_ID))
    }

    @Test
    fun leavingTheAppMidIntroductionPlaysItAgainNextTime() = runTest(dispatcher) {
        player.untilStopped = true
        runCurrent()
        viewModel.introduceIfFirstVisit()
        runCurrent()
        viewModel.onLeftApp() // the app went to the background (not a rotation: the screen skips those)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.open)
        assertFalse(settings.introduced(SampleTasks.PROJECT_ID))

        player.untilStopped = false
        viewModel.introduceIfFirstVisit()
        advanceUntilIdle()
        assertEquals(listOf("intro", "intro"), api.said)
        assertTrue(settings.introduced(SampleTasks.PROJECT_ID))
    }

    @Test
    fun theIntroductionCanBeCutShort() = runTest(dispatcher) {
        player.untilStopped = true
        runCurrent()
        viewModel.introduceIfFirstVisit()
        runCurrent()
        assertTrue(viewModel.state.value.open) // still introducing
        viewModel.close()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.open)
        assertTrue(settings.introduced(SampleTasks.PROJECT_ID)) // not repeated next time
    }

    /** The user will say [words] (one per turn; null = silence; a leading "~" = said faintly, from across the room). */
    private fun says(vararg words: String?) {
        words.forEach { word ->
            recorder.turns.addLast(
                when {
                    word == null -> 0
                    word.startsWith("~") -> 2_500
                    else -> 8_000
                },
            )
            if (word != null) api.heard.addLast(word.removePrefix("~"))
        }
    }

    private fun TestScope.talk() {
        runCurrent() // availability is worked out
        viewModel.open()
        advanceUntilIdle()
    }

    @Test
    fun aSpokenYesAppliesThenTheOrbClosesBeforeSayingWhatChanged() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour", "yes")
        var highlightWhenExplainingApplied: Set<String>? = null
        api.onExplain = {
            if (it is ExplainRequest.Changes && it.stage == ExplainStage.Applied) highlightWhenExplainingApplied = viewModel.highlight.value
        }
        talk()

        assertEquals(originalReview.startMillis + 3_600_000L, review.startMillis)
        val stages = api.explained.filterIsInstance<ExplainRequest.Changes>().map { it.stage }
        assertEquals(listOf(ExplainStage.Proposal, ExplainStage.Applied), stages)
        assertEquals(setOf("review"), highlightWhenExplainingApplied)
        assertEquals(emptySet<String>(), viewModel.highlight.value) // cleared once it has finished speaking
        assertFalse(viewModel.state.value.open)
        val applied = viewModel.message.value as AssistantMessage.Applied
        assertEquals(1, applied.taskCount)

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(originalReview, review)
    }

    @Test
    fun itStaysBusyFromOpeningUntilItHasSaidWhatChanged() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour", "yes")
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.busy.collect { seen += it } }
        // The explanation of what changed keeps playing until the test lets it finish.
        api.onExplain = { if (it is ExplainRequest.Changes && it.stage == ExplainStage.Applied) player.untilStopped = true }
        talk()

        assertFalse(viewModel.state.value.open) // the orb has gone...
        assertEquals(setOf("review"), viewModel.highlight.value) // ...and it is saying what changed
        assertTrue(viewModel.busy.value)

        player.stop() // it finishes
        advanceUntilIdle()
        assertFalse(viewModel.busy.value)
        assertEquals(listOf(false, true, false), seen) // one unbroken busy stretch, then free again
    }

    @Test
    fun closingTheOrbOrLeavingTheAppEndsBusy() = runTest(dispatcher) {
        says(null)
        runCurrent()
        viewModel.open()
        runCurrent()
        assertTrue(viewModel.busy.value)
        viewModel.close()
        runCurrent()
        assertFalse(viewModel.busy.value)

        viewModel.open()
        runCurrent()
        viewModel.onLeftApp()
        runCurrent()
        assertFalse(viewModel.busy.value)
    }

    @Test
    fun aClashIsShownAndNamedBeforeAskingAndOnlyAYesAppliesIt() = runTest(dispatcher) {
        api.answer = shift(-1) // Review 15:00–16:00 runs into QA Testing (13:30–16:00)
        player.holdIn = this
        val shownWhileAsking = mutableListOf<List<ScheduleWarning>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { if (it.proposal.isNotEmpty()) shownWhileAsking += it.warnings }
        }
        says("move review an hour earlier", "yes")
        talk()

        val overlap = listOf(ScheduleWarning.Overlap("Review", "QA Testing"))
        val sent = api.explained.filterIsInstance<ExplainRequest.Changes>()
        assertEquals(listOf(ExplainStage.Proposal, ExplainStage.Applied), sent.map { it.stage })
        assertEquals(overlap, sent[0].warnings)
        assertEquals(emptyList<ScheduleWarning>(), sent[1].warnings) // once applied, nothing to warn about
        assertTrue(shownWhileAsking.isNotEmpty() && shownWhileAsking.all { it == overlap })
        assertEquals(originalReview.startMillis - 3_600_000L, review.startMillis)
        assertEquals(emptyList<ScheduleWarning>(), viewModel.state.value.warnings)
    }

    @Test
    fun theModelsClashFreeAlternativeIsOfferedAndAYesAppliesIt() = runTest(dispatcher) {
        api.answer = shift(-1) // Review 15:00–16:00 runs into QA Testing
        api.suggest = { c -> shift(1)("", c) } // Review 17:00–18:00 is free
        player.holdIn = this
        val offered = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { if (it.proposal.isNotEmpty()) offered += it.offer.size }
        }
        says("move review an hour earlier", "yes")
        talk()

        assertEquals(listOf(ProposedOperation("shift_task", taskId = "review", amount = -1, unit = "hours")), api.resolved.single().first)
        assertEquals(listOf(ScheduleWarning.Overlap("Review", "QA Testing")), api.resolved.single().second)
        val proposal = api.explained.filterIsInstance<ExplainRequest.Changes>().first()
        assertEquals(1, proposal.offer.size)
        assertEquals("Review", proposal.offer.single().task)
        assertTrue(offered.isNotEmpty() && offered.all { it == 1 }) // the offer is shown while asking
        assertEquals(originalReview.startMillis + 3_600_000L, review.startMillis) // the alternative, not the clash
        assertEquals(listOf(DecisionAnswer.AcceptedFix), api.decisions.map { it.answer })
        assertEquals(1, api.decisions.single().offered.size)
    }

    @Test
    fun theAlternativeThatCameWithTheProposalIsUsedWithoutAnotherRequest() = runTest(dispatcher) {
        val later = ProposedOperation("shift_task", taskId = "review", amount = 1, unit = "hours")
        api.answer = { text, c -> shift(-1)(text, c).copy(alternative = listOf(later)) }
        says("move review an hour earlier", "yes")
        talk()
        assertTrue(api.resolved.isEmpty()) // no second model call
        assertEquals("Review", api.explained.filterIsInstance<ExplainRequest.Changes>().first().offer.single().task)
        assertEquals(originalReview.startMillis + 3_600_000L, review.startMillis)
        assertEquals(listOf(DecisionAnswer.AcceptedFix), api.decisions.map { it.answer })
    }

    @Test
    fun anAlternativeThatStillClashesIsReplacedByAskingForAnother() = runTest(dispatcher) {
        val stillInQa = ProposedOperation("shift_task", taskId = "review", amount = -2, unit = "hours")
        api.answer = { text, c -> shift(-1)(text, c).copy(alternative = listOf(stillInQa)) }
        api.suggest = { c -> shift(1)("", c) }
        says("move review an hour earlier", "yes")
        talk()
        assertEquals(1, api.resolved.size)
        assertEquals(originalReview.startMillis + 3_600_000L, review.startMillis)
    }

    @Test
    fun aSuggestionThatStillClashesOrFailsIsNotOffered() = runTest(dispatcher) {
        api.answer = shift(-1)
        api.suggest = { c -> shift(-2)("", c) } // Review 14:00–15:00: still inside QA Testing
        says("move review an hour earlier", "yes")
        talk()
        assertEquals(emptyList<ExplainedChange>(), api.explained.filterIsInstance<ExplainRequest.Changes>().first().offer)
        assertEquals(originalReview.startMillis - 3_600_000L, review.startMillis) // applied as asked
        assertEquals(listOf(DecisionAnswer.Accepted), api.decisions.map { it.answer })
    }

    @Test
    fun withoutASuggestionABrokenLinkIsFixedByPushingWhatWaits() = runTest(dispatcher) {
        // QA Testing (13:30–16:00) is to wait for Review (16:00–17:00).
        api.answer = { _, c -> Proposal(c.revision, "operations", listOf(ProposedOperation("add_dependency", predecessorId = "review", successorId = "qa-testing")), null, null) }
        api.resolveFailure = VoiceApiException.Kind.UNAVAILABLE
        player.holdIn = this
        val shown = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { if (it.proposal.isNotEmpty()) shown += it.offer.size }
        }
        says("qa testing waits for review", "yes")
        talk()

        // The link is in both versions: only the move is shown as the alternative.
        assertTrue(shown.isNotEmpty() && shown.all { it == 1 })

        val offer = api.explained.filterIsInstance<ExplainRequest.Changes>().first().offer
        assertEquals(listOf("linked", "moved"), offer.map { it.change })
        val qa = repository.tasks.value.first { it.id == "qa-testing" }
        assertEquals(review.endMillis, qa.startMillis) // starts right when Review ends
        assertEquals(listOf(DecisionAnswer.AcceptedFix), api.decisions.map { it.answer })
    }

    @Test
    fun turningTheAlternativeDownAsksOnceMoreAndKeepsTheirOwnVersion() = runTest(dispatcher) {
        api.answer = shift(-1)
        api.suggest = { c -> shift(1)("", c) }
        says("move review an hour earlier", "no", "yes")
        talk()
        assertEquals(listOf("hello", "as asked?"), api.said)
        assertEquals(originalReview.startMillis - 3_600_000L, review.startMillis) // their version, clash and all
        // The request is heard with the task names; both answers also with "no", never "yes" (noise was heard as yes).
        assertEquals(listOf(false, true, true), api.keyterms.map { "no" in it })
        assertTrue(api.keyterms.none { "yes" in it })
        assertTrue(api.keyterms.all { "Review" in it })
        assertEquals(listOf(DecisionAnswer.KeptAsIs), api.decisions.map { it.answer })
    }

    @Test
    fun noToBothChangesNothing() = runTest(dispatcher) {
        api.answer = shift(-1)
        api.suggest = { c -> shift(1)("", c) }
        says("move review an hour earlier", "no", "no")
        talk()
        assertEquals(originalReview, review)
        assertTrue(repository.applied.isEmpty())
        assertEquals(listOf(DecisionAnswer.Declined), api.decisions.map { it.answer })
    }

    @Test
    fun everyAnswerIsReportedButAFailedReportChangesNothing() = runTest(dispatcher) {
        api.answer = { text, c -> shift(if ("two hours" in text) 2 else 1)(text, c) }
        says("push review by an hour", "make it two hours instead", "yes")
        talk()
        assertEquals(listOf(DecisionAnswer.Corrected, DecisionAnswer.Accepted), api.decisions.map { it.answer })
        assertEquals("make it two hours instead", api.decisions[0].reply)
        assertEquals("push review by an hour", api.decisions[0].said)
        assertEquals("Review", api.decisions[0].proposed.single().task)

        api.decideFailure = VoiceApiException.Kind.NETWORK
        says("push review by an hour", "no")
        talk()
        assertEquals(originalReview.startMillis + 2 * 3_600_000L, review.startMillis) // the second "no" changed nothing, failure or not
        assertFalse(viewModel.state.value.open)
    }

    @Test
    fun aChangeWithoutClashesSendsNoWarnings() = runTest(dispatcher) {
        api.answer = shift(1) // Review 17:00–18:00, after everything else
        says("push review by an hour", "no")
        talk()
        assertEquals(listOf(emptyList<ScheduleWarning>()), api.explained.filterIsInstance<ExplainRequest.Changes>().map { it.warnings })
    }

    @Test
    fun aRejectedTokenIsSaidAndEndsTheSession() = runTest(dispatcher) {
        api.transcribeFailure = VoiceApiException.Kind.UNAUTHORIZED
        says("push review by an hour")
        talk()
        assertEquals(listOf("hello", "failure UNAUTHORIZED"), api.said)
        assertTrue(auth.ended)
    }

    @Test
    fun anAppliedChangeIsRecordedWithWhatUndoesIt() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour", "yes")
        talk()
        val added = notifications.added.single()
        assertEquals(NotificationKind.ASSISTANT_APPLIED, added.notification.kind)
        assertEquals(SampleTasks.PROJECT_ID, added.notification.projectId)
        assertEquals("Sample project", added.notification.projectName)
        assertEquals(mapOf("count" to "1", "tasks" to "Review"), added.notification.args)
        assertEquals(originalReview, (added.undo.single() as ScheduleChange.UpdateTask).after) // undo puts it back
    }

    @Test
    fun aChangeAppliedDespiteAClashIsRecordedAsSuch() = runTest(dispatcher) {
        api.answer = shift(-1) // overlaps QA Testing; no alternative
        says("move review an hour earlier", "yes")
        talk()
        val added = notifications.added.single().notification
        assertEquals(NotificationKind.ASSISTANT_CLASH, added.kind)
        assertEquals(mapOf("clash" to "overlap", "first" to "Review", "second" to "QA Testing"), added.args.filterKeys { it in setOf("clash", "first", "second") })
    }

    @Test
    fun nothingIsRecordedWithoutAChange() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour", "no")
        talk()
        assertTrue(notifications.added.isEmpty())
    }

    @Test
    fun noLeavesEverythingAsItWas() = runTest(dispatcher) {
        api.answer = shift(1)
        val linesWhileDeclining = mutableListOf<Int>()
        player.holdIn = this
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { if (it.saying == "nothing changed") linesWhileDeclining += it.proposal.size }
        }
        says("push review by an hour", "no")
        talk()
        assertEquals(originalReview, review)
        assertTrue(repository.applied.isEmpty())
        assertEquals(listOf("hello", "nothing changed"), api.said)
        assertTrue(linesWhileDeclining.isNotEmpty() && linesWhileDeclining.all { it == 0 }) // the change lines are gone, so nothing looks applied
        assertFalse(viewModel.state.value.open)
    }

    @Test
    fun anythingElseIsACorrectedRequest() = runTest(dispatcher) {
        api.answer = { text, c -> shift(if ("two hours" in text) 2 else 1)(text, c) }
        says("push review by an hour", "no, make it two hours instead", "yes")
        talk()
        assertEquals(2, api.requests.size)
        assertTrue(api.requests[1], api.requests[1].startsWith("push review by an hour. Change of plan: no, make it two hours"))
        assertEquals(originalReview.startMillis + 2 * 3_600_000L, review.startMillis)
    }

    @Test
    fun aQuestionIsAskedAloudAndTheAnswerJoinsTheRequest() = runTest(dispatcher) {
        api.answer = { text, c ->
            if ("Answer to" in text) shift(1)(text, c) else Proposal(c.revision, "clarification", emptyList(), "Which review?", null)
        }
        says("move the review", "design review", "yes")
        talk()
        assertEquals(listOf("hello", "Which review?"), api.said)
        assertEquals("move the review. Answer to \"Which review?\": design review", api.requests[1])
        assertEquals(originalReview.startMillis + 3_600_000L, review.startMillis)
    }

    @Test
    fun silenceTwiceSaysGoodbyeWithoutCallingTheServer() = runTest(dispatcher) {
        says(null, null)
        talk()
        assertEquals(listOf("hello", "say again", "goodbye"), api.said)
        assertTrue(api.requests.isEmpty())
        assertFalse(viewModel.state.value.open)
        assertTrue(repository.applied.isEmpty())
    }

    @Test
    fun itSaysHelloFirstAndFetchesTheGreetingOnlyOnce() = runTest(dispatcher) {
        says(null, null)
        talk()
        assertEquals("hello", api.said.first())
        says(null, null)
        talk()
        assertEquals(listOf("hello", "say again", "goodbye", "say again", "goodbye"), api.said)
    }

    @Test
    fun aProblemIsSaidAndAnotherMissEndsTheConversation() = runTest(dispatcher) {
        api.answer = { _, c -> Proposal(c.revision, "unsupported", emptyList(), null, "Only schedules.") }
        says("what's the weather", null)
        talk()
        assertEquals(listOf("hello", "Only schedules.", "goodbye"), api.said)
        assertTrue(repository.applied.isEmpty())
    }

    @Test
    fun aSoundWithNoWordsGetsOneMoreListenBeforeTheAnswer() = runTest(dispatcher) {
        // Seen on the phone: a noise ended the turn and came back as no words; it isn't a "no".
        api.answer = shift(1)
        says("push review by an hour", "", "yes")
        talk()
        assertEquals(originalReview.startMillis + 3_600_000L, review.startMillis)
    }

    @Test
    fun twoSoundsWithNoWordsAreNoAnswer() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour", "", "", "yes")
        talk()
        assertEquals(originalReview, review)
        assertEquals(listOf("hello", "nothing changed"), api.said)
    }

    @Test
    fun aFaintYesIsAskedAboutOnceAndAClearYesThenApplies() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour", "~yes", "yes")
        talk()
        assertEquals(listOf("hello", "was that a yes?"), api.said)
        assertEquals(originalReview.startMillis + 3_600_000L, review.startMillis)
    }

    @Test
    fun aFaintYesTwiceChangesNothing() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour", "~yes", "~yes")
        talk()
        assertEquals(originalReview, review)
        assertTrue(repository.applied.isEmpty())
        assertEquals(listOf("hello", "was that a yes?", "nothing changed"), api.said)
        assertTrue(api.decisions.isEmpty()) // not a decision
    }

    @Test
    fun aFaintNoOrAFaintRequestNeedsNoSecondQuestion() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour", "~no")
        talk()
        assertEquals(listOf("hello", "nothing changed"), api.said) // a faint no is safe as it is
        assertEquals(originalReview, review)
    }

    @Test
    fun aFaintYesToTheAlternativeIsAskedAboutToo() = runTest(dispatcher) {
        api.answer = shift(-1)
        api.suggest = { c -> shift(1)("", c) }
        says("move review an hour earlier", "~yes", "~yes")
        talk()
        assertEquals(originalReview, review)
        assertEquals(listOf("hello", "was that a yes?", "nothing changed"), api.said)
    }

    @Test
    fun noAnswerToTheProposalChangesNothing() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour", null)
        talk()
        assertEquals(originalReview, review)
        assertEquals(listOf("hello", "nothing changed"), api.said)
    }

    @Test
    fun anEditBeforeYesIsNotOverwritten() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour", "yes")
        var transcripts = 0
        api.onTranscribe = {
            transcripts++
            if (transcripts == 2) runBlocking { repository.updateTask(review.copy(title = "Edited by hand")) }
        }
        talk()
        assertEquals("Edited by hand", review.title)
        assertEquals(originalReview.startMillis, review.startMillis)
        assertEquals(listOf("hello", "schedule changed"), api.said)
        assertNull(viewModel.message.value)
    }

    @Test
    fun tappingOutsideStopsListeningAndAppliesNothing() = runTest(dispatcher) {
        api.answer = shift(1)
        says("push review by an hour")
        runCurrent()
        viewModel.open()
        advanceTimeBy(500)
        assertTrue(recorder.recording)
        viewModel.close()
        advanceUntilIdle()
        assertFalse(recorder.recording)
        assertTrue(api.requests.isEmpty())
        assertTrue(repository.applied.isEmpty())
        assertFalse(viewModel.state.value.open)
    }

    @Test
    fun offlineOrSignedOutNeverOpens() = runTest(dispatcher) {
        online.value = false
        runCurrent()
        viewModel.open()
        runCurrent()
        assertEquals(AssistantMessage.NeedsConnection, viewModel.message.value)
        assertFalse(viewModel.state.value.open)

        online.value = true
        auth.state.value = AuthState.SignedOut
        runCurrent()
        viewModel.open()
        runCurrent()
        assertEquals(AssistantMessage.NeedsSignIn, viewModel.message.value)
        assertEquals(0, recorder.starts)
    }

    @Test
    fun aBusyMicrophoneOrFailingServerIsSaidAndEndsIt() = runTest(dispatcher) {
        recorder.unavailable = true
        talk()
        assertEquals(listOf("hello", "mic busy"), api.said)

        recorder.unavailable = false
        api.explained.clear()
        api.transcribeFailure = VoiceApiException.Kind.RATE_LIMITED
        says("push review by an hour")
        talk()
        assertEquals(listOf("failure RATE_LIMITED"), api.said)
        assertTrue(repository.applied.isEmpty())
    }

    @Test
    fun theNoticeIsRemembered() = runTest(dispatcher) {
        assertFalse(viewModel.state.value.noticeAccepted)
        viewModel.acceptNotice()
        runCurrent()
        assertTrue(settings.noticeAccepted)
        assertTrue(viewModel.state.value.noticeAccepted)
    }
}
