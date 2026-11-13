package noel.chronova.feature.chartimport.domain

import java.time.LocalDate
import noel.chronova.core.model.Project

/**
 * What the server read from an image of a chart (`POST /v1/import/image`, docs/import-api.md).
 * Days are whole and inclusive. With [Dates.ABSOLUTE] each task has calendar dates; with
 * [Dates.RELATIVE] only day numbers (0 = the chart's first day), and the user picks a start date.
 */
data class ImportedSchedule(
    val result: Result,
    val dates: Dates,
    /** The chart's title, or "" when it has none. */
    val projectName: String,
    /** With absolute dates: the first and last days of the chart. */
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    /** With relative dates: how many days the chart spans. */
    val days: Int?,
    val tasks: List<ImportedTask>,
    /** Short notes about anything the reading guessed. */
    val uncertain: List<String>,
) {
    enum class Result { OK, NOT_A_SCHEDULE, UNREADABLE }

    enum class Dates { ABSOLUTE, RELATIVE }
}

data class ImportedTask(
    /** Names the task within this answer (links use it). */
    val ref: String,
    val title: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val startDay: Int?,
    val endDay: Int?,
    val isMilestone: Boolean,
    val progress: Int,
    val predecessorRefs: List<String>,
)

/** Where the tasks go: a new project made from the chart, or a project that exists. */
sealed interface ImportTarget {
    data object NewProject : ImportTarget

    data class Existing(val project: Project) : ImportTarget
}

/** Reads an image of a chart on the server. */
interface ImportApi {
    /** [image] is a JPEG ([ImageToSend]); [project] is set when adding to it. */
    suspend fun read(accessToken: String, image: ByteArray, timeZone: String, today: LocalDate, project: Project?): ImportedSchedule
}

class ImportApiException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind {
        /** Not signed in, or the session ended. */
        UNAUTHORIZED,

        /** The server has imports switched off. */
        DISABLED,

        /** Too many imports for now (a few a minute, 50 a day). */
        RATE_LIMITED,

        /** The image couldn't be used (not an image, too big). */
        BAD_IMAGE,

        /** The server or a model provider failed, or the answer couldn't be read. */
        UNAVAILABLE,

        /** No connection. */
        NETWORK,
    }
}

/** The images an import reads: photos taken for it, and any image the user picks, by content URI. */
interface ChartImages {
    /** A new, empty file the camera app can write a photo into, as a content URI. */
    suspend fun newPhotoUri(): String

    /**
     * The image at [uri] turned upright, scaled to at most [ImageToSend.MAX_SIDE_PX] on its long side
     * and re-encoded as JPEG, which drops its metadata (location included).
     *
     * @throws ImportApiException with [ImportApiException.Kind.BAD_IMAGE] when it can't be read
     */
    suspend fun prepare(uri: String): ByteArray

    /** Deletes the photos taken for imports. */
    suspend fun clear()
}

/** What the phone sends: the image scaled to at most this many pixels on its long side, as JPEG. */
object ImageToSend {
    const val MAX_SIDE_PX = 2048
    const val JPEG_QUALITY = 85
}
