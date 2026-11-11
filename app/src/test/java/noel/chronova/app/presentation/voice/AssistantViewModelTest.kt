package noel.chronova.app.presentation.voice

import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import noel.chronova.app.auth.AuthOutcome
import noel.chronova.app.auth.AuthRepository
import noel.chronova.app.auth.AuthState
import noel.chronova.app.data.remote.VoiceApi
import noel.chronova.app.data.remote.VoiceApiException
import noel.chronova.app.domain.SampleTasks
import noel.chronova.app.domain.voice.Proposal
import noel.chronova.app.domain.voice.ProposedOperation
import noel.chronova.app.domain.voice.VoiceContext
import noel.chronova.app.presentation.timeline.FakeProjectRepository
import org.junit.After
import org.junit.Assert.assertEquals
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
    val transcripts = mutableListOf<String>()
    var beforeAnswer: suspend () -> Unit = {}

    override suspend fun interpret(accessToken: String, transcript: String, context: VoiceContext): Proposal {
        transcripts += transcript
        gate?.await()
        beforeAnswer()
        failure?.let { throw VoiceApiException(it) }
        return answer(context)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val zone = ZoneOffset.UTC
    private val repository = FakeProjectRepository(listOf(SampleTasks.project), SampleTasks.tasks(zone))
    private val api = FakeVoiceApi()
    private val auth = FakeAuth(AuthState.SignedIn("u1", "ana@example.com"))
    private val online = MutableStateFlow(true)
    private lateinit var viewModel: AssistantViewModel

    private val review get() = repository.tasks.value.first { it.id == "review" }
    private val shiftReview = { context: VoiceContext ->
        Proposal(context.revision, "operations", listOf(ProposedOperation("shift_task", taskId = "review", amount = 1, unit = "hours")), null, null)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AssistantViewModel(SampleTasks.PROJECT_ID, repository, api, auth, online, zone, clock = { 0L }, newId = { "new" })
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
}
