package noel.chronova.feature.assistant.presentation

import noel.chronova.core.domain.schedule.ScheduleChange
import noel.chronova.feature.assistant.domain.PreviewItem
import noel.chronova.feature.assistant.domain.ScheduleWarning
import noel.chronova.feature.assistant.presentation.orb.OrbMode

/** Whether the assistant can be used right now. */
enum class AssistantAvailability {
    /** This build has no server: the assistant isn't offered at all. */
    NOT_CONFIGURED,
    SIGNED_OUT,
    OFFLINE,
    READY,
}

/** The centre orb's state; it only exists while the orb is open. */
data class AssistantUiState(
    val availability: AssistantAvailability = AssistantAvailability.NOT_CONFIGURED,
    val open: Boolean = false,
    val mode: OrbMode = OrbMode.Listening,
    /** Caption: what the assistant heard last. */
    val heard: String? = null,
    /** Caption: what the assistant is saying. */
    val saying: String? = null,
    /** The changes it is asking about, shown under the orb while it waits for yes or no. */
    val proposal: List<PreviewItem> = emptyList(),
    /** Clashes the proposal would cause, shown under its lines; the assistant names them before asking. */
    val warnings: List<ScheduleWarning> = emptyList(),
    /** A clash-free alternative the assistant is offering instead, shown under the warnings. */
    val offer: List<PreviewItem> = emptyList(),
    /** The one-time notice (what goes to ElevenLabs and OpenAI) has been accepted. */
    val noticeAccepted: Boolean = false,
)

/** Things to tell the user on the timeline, outside the orb. */
sealed interface AssistantMessage {
    data class Applied(val taskCount: Int, val undo: List<ScheduleChange>) : AssistantMessage
    data object UndoNotPossible : AssistantMessage
    data object NeedsConnection : AssistantMessage
    data object NeedsSignIn : AssistantMessage
    data object MicrophoneDenied : AssistantMessage

    /** The orb was tapped but the server didn't answer: it stays closed. */
    data object ServerUnreachable : AssistantMessage
}
