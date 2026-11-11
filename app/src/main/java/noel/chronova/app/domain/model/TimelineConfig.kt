package noel.chronova.app.domain.model

import kotlin.math.roundToInt

/**
 * Unit labelled on the time axis. [minutes] is the length of one unit; zoom
 * (the on-screen width of one unit) has its own default and limits per scale.
 */
enum class TimeScale(
    val minutes: Long,
    val defaultUnitWidthDp: Float,
    val minUnitWidthDp: Float,
    val maxUnitWidthDp: Float,
) {
    HOURS(minutes = 60, defaultUnitWidthDp = 96f, minUnitWidthDp = 48f, maxUnitWidthDp = 288f),
    DAYS(minutes = 24 * 60, defaultUnitWidthDp = 480f, minUnitWidthDp = 240f, maxUnitWidthDp = 1440f),
}

/**
 * User-adjustable view settings. None of these change task timestamps — they only
 * change how time is projected onto the screen and how edits are snapped.
 *
 * @param unitWidthDp zoom: on-screen width of one [timeScale] unit.
 * @param snapMinutes grid that drag/resize results are rounded to.
 */
data class TimelineConfig(
    val timeScale: TimeScale = TimeScale.HOURS,
    val unitWidthDp: Float = timeScale.defaultUnitWidthDp,
    val snapMinutes: Int = 15,
    val rowHeightDp: Float = DEFAULT_ROW_HEIGHT_DP,
) {
    /** Zoom relative to the scale's default, e.g. 100 for the default width. */
    val zoomPercent: Int get() = (unitWidthDp / timeScale.defaultUnitWidthDp * 100).roundToInt()

    val canZoomIn: Boolean get() = unitWidthDp < timeScale.maxUnitWidthDp
    val canZoomOut: Boolean get() = unitWidthDp > timeScale.minUnitWidthDp
    val canGrowRows: Boolean get() = rowHeightDp < MAX_ROW_HEIGHT_DP
    val canShrinkRows: Boolean get() = rowHeightDp > MIN_ROW_HEIGHT_DP

    /** Switching scale starts at that scale's default zoom. */
    fun withTimeScale(scale: TimeScale): TimelineConfig =
        if (scale == timeScale) this else copy(timeScale = scale, unitWidthDp = scale.defaultUnitWidthDp)

    fun zoomedIn(): TimelineConfig = withUnitWidth(unitWidthDp * ZOOM_STEP)

    fun zoomedOut(): TimelineConfig = withUnitWidth(unitWidthDp / ZOOM_STEP)

    fun zoomReset(): TimelineConfig = copy(unitWidthDp = timeScale.defaultUnitWidthDp)

    fun withSnap(minutes: Int): TimelineConfig {
        require(minutes in SNAP_OPTIONS) { "Unsupported snap interval: $minutes" }
        return copy(snapMinutes = minutes)
    }

    fun withRowHeightStep(steps: Int): TimelineConfig = copy(
        rowHeightDp = (rowHeightDp + steps * ROW_HEIGHT_STEP_DP).coerceIn(MIN_ROW_HEIGHT_DP, MAX_ROW_HEIGHT_DP),
    )

    private fun withUnitWidth(widthDp: Float): TimelineConfig =
        copy(unitWidthDp = widthDp.coerceIn(timeScale.minUnitWidthDp, timeScale.maxUnitWidthDp))

    companion object {
        const val ZOOM_STEP = 1.25f
        const val DEFAULT_ROW_HEIGHT_DP = 56f
        const val MIN_ROW_HEIGHT_DP = 40f
        const val MAX_ROW_HEIGHT_DP = 80f
        const val ROW_HEIGHT_STEP_DP = 8f
        val SNAP_OPTIONS = listOf(15, 30, 60)
    }
}

/**
 * The visible span of the timeline. [startMillis] is the single origin every x
 * coordinate is measured from.
 */
data class TimelineWindow(
    val startMillis: Long,
    val endMillis: Long,
) {
    init {
        require(endMillis > startMillis) { "Timeline window must have positive length" }
    }

    val lengthMillis: Long get() = endMillis - startMillis
}
