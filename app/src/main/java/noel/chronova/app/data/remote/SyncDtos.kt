package noel.chronova.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Wire format of `POST /v1/sync` (see backend/README.md). */
@Serializable
data class ChangeDto(
    /** "project", "task" or "dependency". */
    val entity: String,
    val id: String,
    val deleted: Boolean,
    /** Edit time, epoch millis: the last-write-wins clock. */
    val updatedAt: Long,
    /** Entity fields; absent on tombstones. */
    val data: JsonObject? = null,
)

@Serializable
data class SyncRequestDto(val cursor: String?, val changes: List<ChangeDto>)

@Serializable
data class SyncResultDto(val entity: String, val id: String, val status: String)

@Serializable
data class SyncResponseDto(
    val results: List<SyncResultDto>,
    val changes: List<ChangeDto>,
    val cursor: String,
    val hasMore: Boolean,
)

object SyncJson {
    /** Unknown fields from a newer server are ignored; nulls are left out. */
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

object SyncEntities {
    const val PROJECT = "project"
    const val TASK = "task"
    const val DEPENDENCY = "dependency"

    /** Projects before tasks before links, so a record's parents are applied first. */
    fun rank(entity: String): Int = when (entity) {
        PROJECT -> 0
        TASK -> 1
        DEPENDENCY -> 2
        else -> 3
    }
}
