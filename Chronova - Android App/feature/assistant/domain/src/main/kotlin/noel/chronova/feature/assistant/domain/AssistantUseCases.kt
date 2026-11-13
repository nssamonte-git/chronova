package noel.chronova.feature.assistant.domain

/** Everything the assistant does, for one assistant (it owns one recorder and one player). */
class AssistantUseCases(
    val observeAvailability: ObserveVoiceAvailability,
    val checkServer: CheckVoiceServer,
    val notice: VoiceNotice,
    val introduction: ProjectIntroduction,
    val listen: ListenForSpeech,
    val play: PlaySpeech,
    val transcribe: TranscribeSpeech,
    val interpret: InterpretRequest,
    val explain: ExplainChanges,
    val voicePhrase: VoicePhrase,
    val recordDecision: RecordDecision,
    val apply: ApplyVoicePlan,
    val recordChange: RecordVoiceChange,
    val undo: UndoVoiceChange,
    val endSession: EndVoiceSession,
    val planUndo: PlanUndo,
    val createProject: CreateVoiceProject,
    val deleteProject: DeleteVoiceProject,
)
