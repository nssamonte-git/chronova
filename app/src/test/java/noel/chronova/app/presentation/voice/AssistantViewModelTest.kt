package noel.chronova.app.presentation.voice

import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import noel.chronova.app.auth.AuthOutcome
import noel.chronova.app.auth.AuthRepository
import noel.chronova.app.auth.AuthState
import noel.chronova.app.data.remote.ExplainRequest
import noel.chronova.app.data.remote.SpokenAnswer
import noel.chronova.app.data.remote.Transcript
import noel.chronova.app.data.remote.VoiceApi
import noel.chronova.app.data.remote.VoiceApiException
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.voice.ExplainedChange
import noel.chronova.app.domain.voice.Proposal
import noel.chronova.app.domain.voice.ProposedOperation
import noel.chronova.app.domain.voice.VoiceContext
import noel.chronova.app.presentation.timeline.FakeProjectRepository
import noel.chronova.app.voice.RecorderUnavailable
import noel.chronova.app.voice.Recording
import noel.chronova.app.voice.SpeechPlayer
import noel.chronova.app.voice.SpeechSettings
import noel.chronova.app.voice.VoiceRecorder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
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

/** Answers with [answer] for the context it was sent (echoing its revision), or throws [failure]. */
private class FakeVoiceApi : VoiceApi {
    var answer: (VoiceContext) -> Proposal = { Proposal(it.revision, "unsupported", emptyList(), null, null) }
    var failure: VoiceApiException.Kind? = null
    var gate: CompletableDeferred<Unit>? = null
    var interpretGate: CompletableDeferred<Unit>? = null
    val transcripts = mutableListOf<String>()
    var beforeAnswer: suspend () -> Unit = {}
    var heard = "push review by an hour"
    var transcribeFailure: VoiceApiException.Kind? = null
    val uploads = mutableListOf<Triple<Boolean, Long, String?>>()
    val keyterms = mutableListOf<List<String>>()
    var heardLanguage: String? = "fil"
    val explained = mutableListOf<ExplainRequest>()
    var explainGate: CompletableDeferred<Unit>? = null
    var explainFailure: VoiceApiException.Kind? = null
    var spokenAudio: ByteArray? = byteArrayOf(1, 2, 3)

    override suspend fun transcribe(accessToken: String, audio: File, durationMs: Long, language: String?, keyterms: List<String>): Transcript {
        uploads += Triple(audio.exists(), durationMs, language)
        this.keyterms += keyterms
        gate?.await()
        transcribeFailure?.let { throw VoiceApiException(it) }
        return Transcript(heard, heardLanguage)
    }

    override suspend fun explain(accessToken: String, request: ExplainRequest): SpokenAnswer {
        explained += request
        explainGate?.await()
        explainFailure?.let { throw VoiceApiException(it) }
        return SpokenAnswer("Review moves an hour later. Tap Apply to confirm.", spokenAudio, spokenAudio?.let { "audio/mpeg" })
    }

    override suspend fun interpret(accessToken: String, transcript: String, context: VoiceContext): Proposal {
        transcripts += transcript
        gate?.await()
        interpretGate?.await()
        beforeAnswer()
        failure?.let { throw VoiceApiException(it) }
        return answer(context)
    }
}

/** Hands out temp files as recordings; [nothing] makes stop() find nothing usable. */
private class FakeRecorder(private val dir: File) : VoiceRecorder {
    var recording = false
    var unavailable = false
    var nothing = false
    val files = mutableListOf<File>()
    var cancels = 0

    override fun start() {
        if (unavailable) throw RecorderUnavailable(IllegalStateException("busy"))
        recording = true
    }

    override fun stop(): Recording? {
        check(recording)
        recording = false
        if (nothing) return null
        return Recording(File.createTempFile("command", ".m4a", dir).also { files += it }, 2_400)
    }

    override fun cancel() {
        if (recording) cancels++
        recording = false
    }
}

private class FakePlayer : SpeechPlayer {
    override val playing = MutableStateFlow(false)
    val played = mutableListOf<ByteArray>()
    var released = false

    override fun play(audio: ByteArray) {
        played += audio
        playing.value = true
    }

    override fun stop() {
        playing.value = false
    }

    override fun release() {
        released = true
        stop()
    }
}

private class FakeSpeechSettings : SpeechSettings {
    override var muted = false
}

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val zone = ZoneOffset.UTC
    private val repository = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone))
    private val api = FakeVoiceApi()
    private val auth = FakeAuth(AuthState.SignedIn("u1", "ana@example.com"))
    private val online = MutableStateFlow(true)
    private val recorder = FakeRecorder(kotlin.io.path.createTempDirectory("voice").toFile())
    private val player = FakePlayer()
    private val speechSettings = FakeSpeechSettings()
    private lateinit var viewModel: AssistantViewModel

    private val review get() = repository.tasks.value.first { it.id == "review" }
    private val shiftReview = { context: VoiceContext ->
        Proposal(context.revision, "operations", listOf(ProposedOperation("shift_task", taskId = "review", amount = 1, unit = "hours")), null, null)
    }

    @Before
    fun setUp() {
        viewModel = newViewModel(UnconfinedTestDispatcher())
    }

    private fun newViewModel(dispatcher: kotlinx.coroutines.test.TestDispatcher): AssistantViewModel {
        Dispatchers.resetMain()
        Dispatchers.setMain(dispatcher)
        return AssistantViewModel(
            SampleTasks.PROJECT_ID, repository, api, auth, online, recorder, player, speechSettings, zone,
            clock = { 1_000L }, newId = { "new" }, language = { "en" },
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun ask(text: String) {
        viewModel.open()
        viewModel.onInputChange(text)
        viewModel.submit()
    }

    @Test
    fun previewThenApplyThenUndo() = runTest {
        api.answer = shiftReview
        val original = review
        ask("push review by an hour")

        val preview = viewModel.state.value.phase as AssistantPhase.Preview
        assertEquals(1, preview.items.size)
        assertEquals(original, review) // nothing written yet

        viewModel.apply()
        assertEquals(original.startMillis + 3_600_000L, review.startMillis)
        assertEquals(AssistantUiState(availability = AssistantAvailability.READY), viewModel.state.value)
        val applied = viewModel.message.value as AssistantMessage.Applied
        assertEquals(1, applied.taskCount)

        viewModel.undo()
        assertEquals(original, review)
    }

    @Test
    fun cancelOrCloseAppliesNothing() = runTest {
        api.answer = shiftReview
        val original = review
        ask("push review by an hour")
        viewModel.close()
        assertEquals(original, review)
        assertEquals(AssistantPhase.Idle, viewModel.state.value.phase)
        assertTrue(repository.applied.isEmpty())
    }

    @Test
    fun thinkingCanBeCancelled() = runTest {
        api.answer = shiftReview
        api.gate = CompletableDeferred()
        ask("push review by an hour")
        assertEquals(AssistantPhase.Thinking, viewModel.state.value.phase)

        viewModel.cancel()
        api.gate!!.complete(Unit)
        assertEquals(AssistantPhase.Idle, viewModel.state.value.phase)
        assertTrue(repository.applied.isEmpty())
    }

    @Test
    fun anEditWhileWaitingIsNotOverwritten() = runTest {
        api.answer = shiftReview
        api.beforeAnswer = { repository.updateTask(review.copy(title = "Edited by hand")) }
        ask("push review by an hour")
        assertEquals(AssistantPhase.Problem(AssistantProblem.ScheduleChanged), viewModel.state.value.phase)
    }

    @Test
    fun anEditBetweenPreviewAndApplyIsNotOverwritten() = runTest {
        api.answer = shiftReview
        ask("push review by an hour")
        repository.updateTask(review.copy(title = "Edited by hand"))

        viewModel.apply()
        assertEquals(AssistantPhase.Problem(AssistantProblem.ScheduleChanged), viewModel.state.value.phase)
        assertEquals("Edited by hand", review.title)
        assertEquals(SampleTasks.tasks(zone).first { it.id == "review" }.startMillis, review.startMillis)
    }

    @Test
    fun questionsAndServerProblemsAreShown() = runTest {
        api.answer = { Proposal(it.revision, "clarification", emptyList(), "Which review?", null) }
        ask("move the review")
        assertEquals(AssistantPhase.Question("Which review?"), viewModel.state.value.phase)

        api.failure = VoiceApiException.Kind.RATE_LIMITED
        viewModel.onInputChange("move review to tomorrow")
        viewModel.submit()
        assertEquals(AssistantPhase.Problem(AssistantProblem.RateLimited), viewModel.state.value.phase)

        api.failure = VoiceApiException.Kind.DISABLED
        viewModel.submit()
        assertEquals(AssistantPhase.Problem(AssistantProblem.Disabled), viewModel.state.value.phase)
    }

    @Test
    fun offlineOrSignedOutNeverCallsTheServer() = runTest {
        online.value = false
        ask("push review by an hour")
        assertEquals(AssistantAvailability.OFFLINE, viewModel.state.value.availability)
        assertEquals(AssistantPhase.Problem(AssistantProblem.Offline), viewModel.state.value.phase)

        online.value = true
        auth.state.value = AuthState.SignedOut
        viewModel.submit()
        assertEquals(AssistantPhase.Problem(AssistantProblem.SignedOut), viewModel.state.value.phase)
        assertTrue(api.transcripts.isEmpty())
    }

    @Test
    fun enterOnAHardwareKeyboardSends() = runTest {
        api.answer = shiftReview
        viewModel.open()
        viewModel.onInputChange("push review by an hour\n")
        assertEquals(listOf("push review by an hour"), api.transcripts)
        assertTrue(viewModel.state.value.phase is AssistantPhase.Preview)
    }

    @Test
    fun noServerMeansNoAssistant() = runTest {
        auth.state.value = AuthState.NotConfigured
        assertEquals(AssistantAvailability.NOT_CONFIGURED, viewModel.state.value.availability)
    }

    @Test
    fun speakingIsTranscribedShownThenPreviewed() = runTest {
        api.answer = shiftReview
        viewModel.open()
        viewModel.startListening()
        assertEquals(AssistantPhase.Listening(1_000L), viewModel.state.value.phase)
        assertTrue(recorder.recording)

        viewModel.finishListening()
        assertEquals(listOf(Triple(true, 2_400L, "en")), api.uploads)
        // The project's task titles go along, to be heard right.
        assertEquals(listOf(SampleTasks.tasks(zone).map { it.title }), api.keyterms)
        assertEquals("push review by an hour", viewModel.state.value.input)
        assertEquals(listOf("push review by an hour"), api.transcripts)
        assertTrue(viewModel.state.value.phase is AssistantPhase.Preview)
        // The recording is deleted once it has been sent.
        assertFalse(recorder.files.single().exists())
    }

    @Test
    fun theWordsAreShownWhileTheyAreInterpreted() = runTest {
        api.answer = shiftReview
        api.interpretGate = CompletableDeferred()
        viewModel.open()
        viewModel.startListening()
        viewModel.finishListening()
        assertEquals("push review by an hour", viewModel.state.value.input)
        assertEquals(AssistantPhase.Thinking, viewModel.state.value.phase)

        api.interpretGate!!.complete(Unit)
        assertTrue(viewModel.state.value.phase is AssistantPhase.Preview)
    }

    @Test
    fun cancellingWhileListeningOrTranscribingSendsAndAppliesNothing() = runTest {
        viewModel.open()
        viewModel.startListening()
        viewModel.cancel()
        assertEquals(1, recorder.cancels)
        assertEquals(AssistantPhase.Idle, viewModel.state.value.phase)
        assertTrue(api.uploads.isEmpty())

        api.gate = CompletableDeferred()
        viewModel.startListening()
        viewModel.finishListening()
        assertEquals(AssistantPhase.Transcribing, viewModel.state.value.phase)
        viewModel.cancel()
        api.gate!!.complete(Unit)
        assertEquals(AssistantPhase.Idle, viewModel.state.value.phase)
        assertTrue(api.transcripts.isEmpty())
        assertTrue(repository.applied.isEmpty())
        assertFalse(recorder.files.single().exists())
    }

    @Test
    fun closingOrLeavingTheAppDropsTheRecording() = runTest {
        viewModel.open()
        viewModel.startListening()
        viewModel.onLeftApp()
        assertEquals(1, recorder.cancels)
        assertEquals(AssistantPhase.Idle, viewModel.state.value.phase)

        viewModel.startListening()
        viewModel.close()
        assertEquals(2, recorder.cancels)
        assertTrue(api.uploads.isEmpty())
    }

    @Test
    fun listeningStopsByItselfAfterAMinute() {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
        viewModel = newViewModel(dispatcher)
        TestScope(dispatcher).runTest {
            runCurrent() // availability is worked out
            viewModel.open()
            viewModel.startListening()
            advanceTimeBy(AssistantViewModel.MAX_LISTENING_MS - 1)
            assertTrue(recorder.recording)
            advanceTimeBy(2)
            assertFalse(recorder.recording)
            assertEquals(1, api.uploads.size)
        }
    }

    @Test
    fun microphoneAndTranscriptionProblemsAreShown() = runTest {
        viewModel.open()
        viewModel.onMicrophoneDenied()
        assertEquals(AssistantPhase.Problem(AssistantProblem.MicrophoneDenied), viewModel.state.value.phase)

        recorder.unavailable = true
        viewModel.startListening()
        assertEquals(AssistantPhase.Problem(AssistantProblem.MicrophoneUnavailable), viewModel.state.value.phase)
        recorder.unavailable = false

        recorder.nothing = true
        viewModel.startListening()
        viewModel.finishListening()
        assertEquals(AssistantPhase.Problem(AssistantProblem.NothingHeard), viewModel.state.value.phase)
        recorder.nothing = false

        api.heard = ""
        viewModel.startListening()
        viewModel.finishListening()
        assertEquals(AssistantPhase.Problem(AssistantProblem.NothingHeard), viewModel.state.value.phase)

        api.transcribeFailure = VoiceApiException.Kind.TOO_LONG
        viewModel.startListening()
        viewModel.finishListening()
        assertEquals(AssistantPhase.Problem(AssistantProblem.TooLong), viewModel.state.value.phase)
        assertTrue(api.transcripts.isEmpty())
        assertTrue(recorder.files.none { it.exists() })
    }

    @Test
    fun theMicrophoneStaysOffWhenOfflineOrSignedOut() = runTest {
        online.value = false
        viewModel.open()
        viewModel.startListening()
        assertFalse(recorder.recording)
        assertEquals(AssistantPhase.Problem(AssistantProblem.Offline), viewModel.state.value.phase)

        online.value = true
        auth.state.value = AuthState.SignedOut
        viewModel.startListening()
        assertFalse(recorder.recording)
        assertEquals(AssistantPhase.Problem(AssistantProblem.SignedOut), viewModel.state.value.phase)
    }

    private fun speak() {
        viewModel.open()
        viewModel.startListening()
        viewModel.finishListening()
    }

    @Test
    fun aSpokenCommandIsPreviewedFirstThenExplainedAloud() = runTest {
        api.answer = shiftReview
        api.explainGate = CompletableDeferred()
        speak()

        // The preview is up before the spoken answer arrives.
        assertTrue(viewModel.state.value.phase is AssistantPhase.Preview)
        assertNull(viewModel.state.value.explanation)
        assertTrue(player.played.isEmpty())

        api.explainGate!!.complete(Unit)
        val state = viewModel.state.value
        assertEquals("Review moves an hour later. Tap Apply to confirm.", state.explanation)
        assertTrue(state.canReplay)
        assertTrue(state.speaking)
        assertArrayEquals(byteArrayOf(1, 2, 3), player.played.single())

        val request = api.explained.single() as ExplainRequest.Changes
        assertEquals("fil", request.language) // the language it was heard in
        assertEquals(
            listOf(
                ExplainedChange(
                    "moved", task = "Review",
                    fromStart = LocalDateTime.of(2026, 3, 2, 16, 0), fromEnd = LocalDateTime.of(2026, 3, 2, 17, 0),
                    toStart = LocalDateTime.of(2026, 3, 2, 17, 0), toEnd = LocalDateTime.of(2026, 3, 2, 18, 0),
                ),
            ),
            request.changes,
        )

        viewModel.replay()
        assertEquals(2, player.played.size)
    }

    @Test
    fun typedCommandsAreAnsweredOnScreenOnly() = runTest {
        api.answer = shiftReview
        ask("push review by an hour")
        assertTrue(viewModel.state.value.phase is AssistantPhase.Preview)
        assertTrue(api.explained.isEmpty())
        assertFalse(viewModel.state.value.canReplay)
    }

    @Test
    fun aSpokenQuestionIsAskedAloud() = runTest {
        api.answer = { Proposal(it.revision, "clarification", emptyList(), "Which review?", null) }
        api.heardLanguage = null
        speak()
        assertEquals(AssistantPhase.Question("Which review?"), viewModel.state.value.phase)
        assertEquals(ExplainRequest.Question("Which review?", "en"), api.explained.single())
        assertNull(viewModel.state.value.explanation) // the question is already on screen
        assertEquals(1, player.played.size)
    }

    @Test
    fun applyCancelOrLeavingStopsTheVoice() = runTest {
        api.answer = shiftReview
        speak()
        assertTrue(player.playing.value)
        viewModel.onLeftApp()
        assertFalse(player.playing.value)

        viewModel.replay()
        viewModel.apply()
        assertFalse(player.playing.value)
        assertNull(viewModel.state.value.explanation)

        speak()
        viewModel.close()
        assertFalse(player.playing.value)
        assertFalse(viewModel.state.value.canReplay)
    }

    @Test
    fun anAnswerArrivingAfterCancelIsNeverPlayed() = runTest {
        api.answer = shiftReview
        api.explainGate = CompletableDeferred()
        speak()
        viewModel.close()
        api.explainGate!!.complete(Unit)
        assertTrue(player.played.isEmpty())
        assertNull(viewModel.state.value.explanation)
    }

    @Test
    fun withoutSpeechTheWordsStillShowAndFailuresChangeNothing() = runTest {
        api.answer = shiftReview
        api.spokenAudio = null
        speak()
        assertEquals("Review moves an hour later. Tap Apply to confirm.", viewModel.state.value.explanation)
        assertFalse(viewModel.state.value.canReplay)
        assertTrue(player.played.isEmpty())

        api.explainFailure = VoiceApiException.Kind.UNAVAILABLE
        speak()
        assertTrue(viewModel.state.value.phase is AssistantPhase.Preview)
        assertNull(viewModel.state.value.explanation)
    }

    @Test
    fun mutingStopsTheVoiceSkipsTheRequestAndIsRemembered() = runTest {
        api.answer = shiftReview
        speak()
        viewModel.toggleMuted()
        assertFalse(player.playing.value)
        assertTrue(speechSettings.muted)
        assertTrue(viewModel.state.value.muted)

        api.explained.clear()
        speak()
        assertTrue(api.explained.isEmpty())
        viewModel.replay()
        assertEquals(1, player.played.size) // only the first, before muting

        viewModel = newViewModel(UnconfinedTestDispatcher())
        assertTrue(viewModel.state.value.muted)
        viewModel.toggleMuted()
        assertFalse(speechSettings.muted)
    }

    @Test
    fun thePlayerIsReleasedWithTheAssistant() {
        val store = androidx.lifecycle.ViewModelStore()
        store.put("assistant", viewModel)
        store.clear()
        assertTrue(player.released)
    }
}
