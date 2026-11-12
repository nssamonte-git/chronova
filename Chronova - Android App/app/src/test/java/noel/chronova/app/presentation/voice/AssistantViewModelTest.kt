package noel.chronova.app.presentation.voice

import java.io.File
import java.time.ZoneOffset
import kotlinx.coroutines.yield
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import noel.chronova.app.auth.AuthOutcome
import noel.chronova.app.auth.AuthRepository
import noel.chronova.app.auth.AuthState
import noel.chronova.app.data.remote.ExplainRequest
import noel.chronova.app.data.remote.ExplainStage
import noel.chronova.app.data.remote.SpokenAnswer
import noel.chronova.app.data.remote.Transcript
import noel.chronova.app.data.remote.VoiceApi
import noel.chronova.app.data.remote.VoiceApiException
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.schedule.CommandProblem
import noel.chronova.app.domain.voice.Proposal
import noel.chronova.app.domain.voice.ProposedOperation
import noel.chronova.app.domain.voice.VoiceContext
import noel.chronova.app.presentation.timeline.FakeProjectRepository
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
    override suspend fun signIn(email: String, password: String) = AuthOutcome.Done
    override suspend fun requestPasswordReset(email: String) = AuthOutcome.Done
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = AuthOutcome.Done
    override suspend fun signOut() = Unit
    override suspend fun accessToken(): String? = if (state.value is AuthState.SignedIn) "token" else null
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

    override suspend fun transcribe(accessToken: String, audio: File, durationMs: Long, language: String?, keyterms: List<String>): Transcript {
        onTranscribe()
        transcribeFailure?.let { throw VoiceApiException(it) }
        return Transcript(heard.removeFirstOrNull() ?: "", "en")
    }

    override suspend fun interpret(accessToken: String, transcript: String, context: VoiceContext): Proposal {
        requests += transcript
        return answer(transcript, context)
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
    val turns = ArrayDeque<Boolean>()
    var unavailable = false
    var recording = false
    var starts = 0
    private var speaking = false
    private var samples = 0

    override fun start() {
        if (unavailable) throw RecorderUnavailable(IllegalStateException("busy"))
        starts++
        recording = true
        speaking = turns.removeFirstOrNull() ?: false
        samples = 0
    }

    override fun amplitude(): Int {
        samples++
        return if (speaking && samples in 4..12) 8_000 else 300
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

    override fun play(audio: ByteArray) {
        played += audio // otherwise finishes at once
        holdIn?.let { scope ->
            playing.value = true
            scope.launch {
                yield()
                playing.value = false
            }
        }
    }

    override fun stop() = Unit

    override fun release() = Unit
}

private class FakeSettings : AssistantSettings {
    override var noticeAccepted = false
}

private object FakePhrases : AssistantPhrases {
    override fun greeting() = "hello"
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
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The user will say [words] (one per turn; null = silence). */
    private fun says(vararg words: String?) {
        words.forEach { word ->
            recorder.turns.addLast(word != null)
            if (word != null) api.heard.addLast(word)
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
