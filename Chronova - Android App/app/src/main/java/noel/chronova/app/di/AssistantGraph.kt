package noel.chronova.app.di

import android.content.Context
import java.time.ZoneId
import java.util.UUID
import noel.chronova.app.AppContainer
import noel.chronova.core.domain.schedule.ApplyScheduleChanges
import noel.chronova.feature.assistant.data.AndroidSpeechPlayer
import noel.chronova.feature.assistant.data.AndroidVoiceRecorder
import noel.chronova.feature.assistant.data.PreferencesAssistantSettings
import noel.chronova.feature.assistant.domain.AICommandHandler
import noel.chronova.feature.assistant.domain.PlanUndo
import noel.chronova.feature.assistant.domain.ApplyVoicePlan
import noel.chronova.feature.assistant.domain.AssistantSettings
import noel.chronova.feature.assistant.domain.AssistantUseCases
import noel.chronova.feature.assistant.domain.CheckVoiceServer
import noel.chronova.feature.assistant.domain.EndVoiceSession
import noel.chronova.feature.assistant.domain.ExplainChanges
import noel.chronova.feature.assistant.domain.InterpretRequest
import noel.chronova.feature.assistant.domain.ListenForSpeech
import noel.chronova.feature.assistant.domain.ObserveVoiceAvailability
import noel.chronova.feature.assistant.domain.PlaySpeech
import noel.chronova.feature.assistant.domain.ProjectIntroduction
import noel.chronova.feature.assistant.domain.RecordDecision
import noel.chronova.feature.assistant.domain.RecordVoiceChange
import noel.chronova.feature.assistant.domain.TranscribeSpeech
import noel.chronova.feature.assistant.domain.UndoVoiceChange
import noel.chronova.feature.assistant.domain.VoiceAccessToken
import noel.chronova.feature.assistant.domain.VoiceNotice
import noel.chronova.feature.assistant.domain.VoicePhrase
import noel.chronova.feature.assistant.domain.schedule.CommandPlanner
import noel.chronova.feature.assistant.presentation.AssistantPhrases
import noel.chronova.feature.assistant.presentation.ResourcePhrases

/** The voice assistant's wiring. */
class AssistantGraph(private val app: AppContainer, private val context: Context) {
    private val settings: AssistantSettings =
        PreferencesAssistantSettings(context.getSharedPreferences("chronova_voice", Context.MODE_PRIVATE))

    /** The assistant's own words (spoken problems, goodbyes), from string resources. */
    val phrases: AssistantPhrases = ResourcePhrases(context.resources)

    /**
     * One assistant's use cases, with its own microphone recorder and answer player (one
     * recording and one answer at a time). The time zone is read when the timeline opens.
     */
    fun useCases(): AssistantUseCases {
        val zone = ZoneId.systemDefault()
        val clock: () -> Long = System::currentTimeMillis
        val voiceApi = app.voiceApi
        val repository = app.projectRepository
        val token = VoiceAccessToken(app.authRepository)
        val applyChanges = ApplyScheduleChanges(repository, zone)
        return AssistantUseCases(
            observeAvailability = ObserveVoiceAvailability(app.authRepository, app.online),
            checkServer = CheckVoiceServer(voiceApi),
            notice = VoiceNotice(settings),
            introduction = ProjectIntroduction(settings),
            listen = ListenForSpeech(AndroidVoiceRecorder(context)),
            play = PlaySpeech(AndroidSpeechPlayer(context)),
            transcribe = TranscribeSpeech(repository, voiceApi, token),
            interpret = InterpretRequest(
                repository, voiceApi, token,
                AICommandHandler(CommandPlanner(zone, newId = { UUID.randomUUID().toString() })), zone, clock,
            ),
            explain = ExplainChanges(voiceApi, token, zone, clock),
            voicePhrase = VoicePhrase(voiceApi, token),
            recordDecision = RecordDecision(voiceApi, token, zone),
            apply = ApplyVoicePlan(applyChanges),
            recordChange = RecordVoiceChange(app.notificationRepository, repository, zone),
            undo = UndoVoiceChange(applyChanges),
            planUndo = PlanUndo(repository),
            endSession = EndVoiceSession(app.authRepository),
        )
    }
}
