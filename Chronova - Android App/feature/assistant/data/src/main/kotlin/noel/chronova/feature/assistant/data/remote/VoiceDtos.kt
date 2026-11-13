package noel.chronova.feature.assistant.data.remote

import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import noel.chronova.feature.assistant.domain.DecisionAnswer
import noel.chronova.feature.assistant.domain.ExplainStage
import noel.chronova.feature.assistant.domain.ExplainedChange
import noel.chronova.feature.assistant.domain.Proposal
import noel.chronova.feature.assistant.domain.ProposedAction
import noel.chronova.feature.assistant.domain.ProposedOperation
import noel.chronova.feature.assistant.domain.ScheduleWarning
import noel.chronova.feature.assistant.domain.VoiceApiException
import noel.chronova.feature.assistant.domain.VoiceContext

// The voice API's wire format: request and response DTOs and their mapping to and from the domain.

@Serializable
internal data class TranscribeResponseDto(val text: String, val language: String? = null)

@Serializable
internal data class ExplainRequestDto(
    val today: String? = null,
    val changes: List<ExplainChangeDto>? = null,
    val question: String? = null,
    val language: String? = null,
    val stage: String? = null,
    val warnings: List<ExplainWarningDto>? = null,
    val offer: List<ExplainChangeDto>? = null,
)

/** Exactly the fields of each kind, like [ExplainChangeDto]. */
@Serializable
internal data class ExplainWarningDto(
    val kind: String,
    val task: String? = null,
    val other: String? = null,
    val predecessor: String? = null,
    val successor: String? = null,
)

internal fun ScheduleWarning.toDto() = when (this) {
    is ScheduleWarning.Overlap -> ExplainWarningDto("overlap", task = task, other = other)
    is ScheduleWarning.BrokenLink -> ExplainWarningDto("dependency", predecessor = predecessor, successor = successor)
}

/** Null fields are left out (`explicitNulls = false`): the server wants exactly the fields of each kind. */
@Serializable
internal data class ExplainChangeDto(
    val change: String,
    val task: String? = null,
    val title: String? = null,
    val predecessor: String? = null,
    val successor: String? = null,
    val fromStart: String? = null,
    val fromEnd: String? = null,
    val toStart: String? = null,
    val toEnd: String? = null,
    val fromProgress: Int? = null,
    val toProgress: Int? = null,
)

@Serializable
internal data class ExplainResponseDto(val text: String, val audio: String? = null, val audioType: String? = null)

internal fun ExplainedChange.toDto() = ExplainChangeDto(
    change = change,
    task = task,
    title = title,
    predecessor = predecessor,
    successor = successor,
    fromStart = fromStart?.wire(),
    fromEnd = fromEnd?.wire(),
    toStart = toStart?.wire(),
    toEnd = toEnd?.wire(),
    fromProgress = fromProgress,
    toProgress = toProgress,
)

/** [capabilities]: what this app can do besides schedule edits ([APP_CAPABILITIES]). */
@Serializable
internal data class InterpretRequestDto(val transcript: String, val context: VoiceContextDto, val capabilities: List<String>)

/** Tells the server this app can do actions (go to today, show a task, ...); older apps don't send it. */
internal val APP_CAPABILITIES = listOf("actions")

@Serializable
internal data class VoiceContextDto(
    val projectId: String,
    val projectName: String,
    val startDate: String,
    val endDate: String,
    val timeZone: String,
    val today: String,
    val now: String,
    val revision: String,
    val tasks: List<VoiceTaskDto>,
)

@Serializable
internal data class VoiceTaskDto(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val predecessorIds: List<String>,
    val progress: Int = 0,
    val milestone: Boolean = false,
)

@Serializable
internal data class DecisionRequestDto(
    val said: String,
    val proposed: List<ExplainChangeDto>,
    val offered: List<ExplainChangeDto>? = null,
    val answer: String,
    val reply: String? = null,
)

/** The fields each operation type always has, nullable ones included (the server checks the exact shape). */
private val OPERATION_FIELDS = mapOf(
    "move_task" to listOf("taskId", "date", "time"),
    "shift_task" to listOf("taskId", "amount", "unit"),
    "set_times" to listOf("taskId", "start", "end"),
    "change_duration" to listOf("taskId", "amount", "unit"),
    "set_duration" to listOf("taskId", "amount", "unit"),
    "rename_task" to listOf("taskId", "title"),
    "create_task" to listOf("title", "start", "end", "predecessorIds"),
    "delete_task" to listOf("taskId"),
    "add_dependency" to listOf("predecessorId", "successorId"),
    "remove_dependency" to listOf("predecessorId", "successorId"),
    "reschedule_dependents" to listOf("taskId"),
    "set_progress" to listOf("taskId", "percent"),
    "create_milestone" to listOf("title", "at", "predecessorIds"),
)

/** An operation as the server sent it: its type's fields, nulls written out. */
internal fun ProposedOperation.toWire(): JsonObject = buildJsonObject {
    put("type", type)
    OPERATION_FIELDS[type].orEmpty().forEach { field ->
        when (field) {
            "taskId" -> put(field, taskId)
            "date" -> put(field, date)
            "time" -> put(field, time)
            "amount" -> put(field, amount)
            "unit" -> put(field, unit)
            "start" -> put(field, start)
            "end" -> put(field, end)
            "title" -> put(field, title)
            "predecessorIds" -> put(field, JsonArray(predecessorIds.orEmpty().map(::JsonPrimitive)))
            "predecessorId" -> put(field, predecessorId)
            "successorId" -> put(field, successorId)
            "percent" -> put(field, percent)
            "at" -> put(field, at)
        }
    }
}

@Serializable
internal data class InterpretResponseDto(
    val revision: String? = null,
    val result: String,
    val operations: List<OperationDto> = emptyList(),
    val question: String? = null,
    val reason: String? = null,
    val alternative: List<OperationDto>? = null,
    val actions: List<ActionDto> = emptyList(),
)

@Serializable
internal data class ActionDto(
    val type: String,
    val date: String? = null,
    val taskId: String? = null,
    val scale: String? = null,
    val direction: String? = null,
    val size: String? = null,
    val on: Boolean? = null,
    val query: String? = null,
    val colors: List<String>? = null,
    val kinds: List<String>? = null,
)

@Serializable
internal data class OperationDto(
    val type: String,
    val taskId: String? = null,
    val date: String? = null,
    val time: String? = null,
    val amount: Long? = null,
    val unit: String? = null,
    val start: String? = null,
    val end: String? = null,
    val title: String? = null,
    val predecessorIds: List<String>? = null,
    val predecessorId: String? = null,
    val successorId: String? = null,
    val percent: Int? = null,
    val at: String? = null,
)

/** Local date-times as the API expects them: minutes, no seconds, no offset. */
// ISO-8601 leaves out zero seconds, giving "2026-11-12T09:00".
private fun LocalDateTime.wire(): String = withSecond(0).withNano(0).toString()

internal fun VoiceContext.toDto() = VoiceContextDto(
    projectId = projectId,
    projectName = projectName,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    timeZone = timeZone,
    today = today.toString(),
    now = now.wire(),
    revision = revision,
    tasks = tasks.map { VoiceTaskDto(it.id, it.title, it.start.wire(), it.end.wire(), it.predecessorIds, it.progress, it.milestone) },
)

internal fun InterpretResponseDto.toDomain() = Proposal(
    revision = revision,
    result = result,
    operations = operations.map { it.toDomain() },
    question = question,
    reason = reason,
    alternative = alternative?.map { it.toDomain() },
    actions = actions.map { ProposedAction(it.type, it.date, it.taskId, it.scale, it.direction, it.size, it.on, it.query, it.colors, it.kinds) },
)

internal fun OperationDto.toDomain() = ProposedOperation(
    type = type,
    taskId = taskId,
    date = date,
    time = time,
    amount = amount,
    unit = unit,
    start = start,
    end = end,
    title = title,
    predecessorIds = predecessorIds,
    predecessorId = predecessorId,
    successorId = successorId,
    percent = percent,
    at = at,
)

/** The API's names for how the user answered (`POST /v1/voice/decision`). */
internal fun DecisionAnswer.toWire(): String = when (this) {
    DecisionAnswer.Accepted -> "accepted"
    DecisionAnswer.Declined -> "declined"
    DecisionAnswer.Corrected -> "corrected"
    DecisionAnswer.AcceptedFix -> "accepted_fix"
    DecisionAnswer.KeptAsIs -> "kept_as_is"
}

/** The API's names for what an explanation is for (`POST /v1/voice/explain`). */
internal fun ExplainStage.toWire(): String = when (this) {
    ExplainStage.Proposal -> "proposal"
    ExplainStage.Applied -> "applied"
}

/** The error for an HTTP status the voice endpoints answered with. */
internal fun voiceErrorFor(code: Int): VoiceApiException.Kind = when (code) {
    401, 403 -> VoiceApiException.Kind.UNAUTHORIZED
    429 -> VoiceApiException.Kind.RATE_LIMITED
    503 -> VoiceApiException.Kind.DISABLED
    in 400..499 -> VoiceApiException.Kind.REJECTED
    else -> VoiceApiException.Kind.UNAVAILABLE
}
