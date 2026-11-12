package noel.chronova.app.presentation.voice

import android.content.res.Resources
import java.time.ZoneId
import noel.chronova.feature.assistant.presentation.R
import noel.chronova.app.domain.voice.VoiceApiException
import noel.chronova.app.domain.TaskProblem
import noel.chronova.app.domain.schedule.CommandProblem
import noel.chronova.app.presentation.TimeFormat

/** What the assistant says (and captions) in its own words, outside the server's explanations. */
interface AssistantPhrases {
    fun greeting(): String

    /** Said once per project, the first time it is opened: who the assistant is and how to use it. */
    fun introduction(): String

    /** After the clash-free alternative was turned down: whether to apply the change as asked. */
    fun applyAsAsked(): String

    /** A yes was heard but only faintly, maybe from across the room. */
    fun wasThatAYes(): String
    fun nothingHeard(): String
    fun goodbye(): String
    fun nothingChanged(): String
    fun notUnderstood(): String
    fun unsupported(reason: String?): String
    fun nothingToChange(): String
    fun scheduleChanged(): String
    fun microphoneUnavailable(): String
    fun failure(kind: VoiceApiException.Kind): String
    fun cannotDo(problem: CommandProblem, titles: Map<String, String>, projectEndMillis: Long): String
}

/** [AssistantPhrases] from the app's string resources. */
class ResourcePhrases(private val resources: Resources, private val zone: ZoneId = ZoneId.systemDefault()) : AssistantPhrases {
    override fun greeting() = resources.getString(R.string.assistant_greeting)
    override fun introduction() = resources.getString(R.string.assistant_introduction)
    override fun applyAsAsked() = resources.getString(R.string.assistant_apply_as_asked)
    override fun wasThatAYes() = resources.getString(R.string.assistant_was_that_a_yes)
    override fun nothingHeard() = resources.getString(R.string.assistant_say_again)
    override fun goodbye() = resources.getString(R.string.assistant_goodbye)
    override fun nothingChanged() = resources.getString(R.string.assistant_nothing_changed)
    override fun notUnderstood() = resources.getString(R.string.assistant_not_understood)
    override fun unsupported(reason: String?) = reason ?: resources.getString(R.string.assistant_unsupported)
    override fun nothingToChange() = resources.getString(R.string.assistant_nothing_to_change)
    override fun scheduleChanged() = resources.getString(R.string.assistant_schedule_changed)
    override fun microphoneUnavailable() = resources.getString(R.string.assistant_mic_unavailable)

    override fun failure(kind: VoiceApiException.Kind) = resources.getString(
        when (kind) {
            VoiceApiException.Kind.UNAUTHORIZED -> R.string.assistant_signed_out
            VoiceApiException.Kind.DISABLED -> R.string.assistant_disabled
            VoiceApiException.Kind.RATE_LIMITED -> R.string.assistant_rate_limited
            VoiceApiException.Kind.NETWORK -> R.string.assistant_offline
            VoiceApiException.Kind.TOO_LONG -> R.string.assistant_too_long
            VoiceApiException.Kind.UNAVAILABLE, VoiceApiException.Kind.REJECTED -> R.string.assistant_unavailable
        },
    )

    override fun cannotDo(problem: CommandProblem, titles: Map<String, String>, projectEndMillis: Long): String {
        fun title(id: String) = titles[id] ?: id
        return when (problem) {
            is CommandProblem.UnknownTask -> resources.getString(R.string.assistant_unknown_task)
            is CommandProblem.InvalidTask -> when (val rule = problem.problem) {
                TaskProblem.BlankTitle -> resources.getString(R.string.assistant_task_blank_title)
                TaskProblem.EndNotAfterStart -> resources.getString(R.string.assistant_task_end_before_start, problem.title)
                is TaskProblem.TooShort -> resources.getQuantityString(R.plurals.assistant_task_too_short, rule.minMinutes, problem.title, rule.minMinutes)
                TaskProblem.OutsideProject -> resources.getString(R.string.assistant_task_outside, problem.title)
            }
            is CommandProblem.WouldCycle -> resources.getString(R.string.assistant_would_cycle, title(problem.predecessorId), title(problem.successorId))
            is CommandProblem.AlreadyLinked -> resources.getString(R.string.assistant_already_linked, title(problem.predecessorId), title(problem.successorId))
            is CommandProblem.NotLinked -> resources.getString(R.string.assistant_not_linked, title(problem.predecessorId), title(problem.successorId))
            is CommandProblem.DoesNotFit -> resources.getString(R.string.assistant_does_not_fit, TimeFormat.day(projectEndMillis - 1, zone))
            is CommandProblem.Rejected -> resources.getString(R.string.assistant_schedule_changed)
        }
    }
}
