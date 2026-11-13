package noel.chronova.core.model

import kotlin.math.roundToInt

/**
 * Unit labelled on the time axis. [minutes] is the nominal length of one unit
 * (months use the average month), which with the zoom gives the on-screen scale;
 * tick positions themselves follow the real calendar.
 *
 * @param minorMinutes nominal spacing of the unlabelled-by-default minor ticks.
 * @param snapOptions snap intervals offered for this scale, in minutes; the first is the default.
 */
enum class TimeScale(
    val minutes: Long,
    val minorMinutes: Long,
    val defaultUnitWidthDp: Float,
    val minUnitWidthDp: Float,
    val maxUnitWidthDp: Float,
    val snapOptions: List<Int>,
) {
    HOURS(
        minutes = 60, minorMinutes = 30,
        defaultUnitWidthDp = 96f, minUnitWidthDp = 48f, maxUnitWidthDp = 288f,
        snapOptions = listOf(15, 30, 60),
    ),
    DAYS(
        minutes = DAY, minorMinutes = 6 * 60,
        defaultUnitWidthDp = 480f, minUnitWidthDp = 240f, maxUnitWidthDp = 1440f,
        snapOptions = listOf(60, 6 * 60, DAY.toInt()),
    ),
    WEEKS(
        minutes = 7 * DAY, minorMinutes = DAY,
        defaultUnitWidthDp = 336f, minUnitWidthDp = 168f, maxUnitWidthDp = 1008f,
        snapOptions = listOf(DAY.toInt()),
    ),
    MONTHS(
        // 365.25 / 12 days.
        minutes = 43_830, minorMinutes = 7 * DAY,
        defaultUnitWidthDp = 300f, minUnitWidthDp = 150f, maxUnitWidthDp = 900f,
        snapOptions = listOf(DAY.toInt(), 7 * DAY.toInt()),
    ),
}

private const val DAY = 24 * 60L

/**
 * User-adjustable view settings. None of these change task timestamps — they only
 * change how time is projected onto the screen and how edits are snapped.
 *
 * @param unitWidthDp zoom: on-screen width of one [timeScale] unit.
 * @param snapMinutes grid that drag/resize results are rounded to; one of the scale's options.
 */
data class TimelineConfig(
    val timeScale: TimeScale = TimeScale.HOURS,
    val unitWidthDp: Float = timeScale.defaultUnitWidthDp,
    val snapMinutes: Int = timeScale.snapOptions.first(),
    val rowHeightDp: Float = DEFAULT_ROW_HEIGHT_DP,
    /** Highlights the tasks where any delay moves the project's finish. */
    val showCriticalPath: Boolean = false,
) {
    /** Zoom relative to the scale's default, e.g. 100 for the default width. */
    val zoomPercent: Int get() = (unitWidthDp / timeScale.defaultUnitWidthDp * 100).roundToInt()

    /** Row height relative to the default, e.g. 100 for 56 dp: shown like zoom, not in dp. */
    val rowHeightPercent: Int get() = (rowHeightDp / DEFAULT_ROW_HEIGHT_DP * 100).roundToInt()

    val canZoomIn: Boolean get() = unitWidthDp < timeScale.maxUnitWidthDp
    val canZoomOut: Boolean get() = unitWidthDp > timeScale.minUnitWidthDp
    val canGrowRows: Boolean get() = rowHeightDp < MAX_ROW_HEIGHT_DP
    val canShrinkRows: Boolean get() = rowHeightDp > MIN_ROW_HEIGHT_DP

    /**
     * Switching scale starts at that scale's default zoom. The snap interval is kept
     * when the new scale offers it, otherwise it falls back to the scale's default.
     */
    fun withTimeScale(scale: TimeScale): TimelineConfig =
        if (scale == timeScale) {
            this
        } else {
            copy(
                timeScale = scale,
                unitWidthDp = scale.defaultUnitWidthDp,
                snapMinutes = snapMinutes.takeIf { it in scale.snapOptions } ?: scale.snapOptions.first(),
            )
        }

    fun zoomedIn(): TimelineConfig = withUnitWidth(unitWidthDp * ZOOM_STEP)

    fun zoomedOut(): TimelineConfig = withUnitWidth(unitWidthDp / ZOOM_STEP)

    /** Zoom by any [factor] (a pinch: above 1 spreads, below 1 squeezes), within the scale's limits. */
    fun zoomedBy(factor: Float): TimelineConfig =
        if (factor.isNaN() || factor <= 0f) this else withUnitWidth(unitWidthDp * factor)

    fun zoomReset(): TimelineConfig = copy(unitWidthDp = timeScale.defaultUnitWidthDp)

    fun withSnap(minutes: Int): TimelineConfig {
        require(minutes in timeScale.snapOptions) { "Unsupported snap interval for $timeScale: $minutes" }
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

        /**
         * Rebuilds a config from stored values, replacing anything out of range
         * (unknown scale, unsupported snap, zoom or row height outside the limits).
         */
        fun restore(
            scaleName: String,
            unitWidthDp: Float,
            snapMinutes: Int,
            rowHeightDp: Float,
            showCriticalPath: Boolean = false,
        ): TimelineConfig {
            val scale = TimeScale.entries.firstOrNull { it.name == scaleName }
                ?: return TimelineConfig(showCriticalPath = showCriticalPath)
            return TimelineConfig(
                timeScale = scale,
                unitWidthDp = unitWidthDp.takeIf { !it.isNaN() }?.coerceIn(scale.minUnitWidthDp, scale.maxUnitWidthDp)
                    ?: scale.defaultUnitWidthDp,
                snapMinutes = snapMinutes.takeIf { it in scale.snapOptions } ?: scale.snapOptions.first(),
                rowHeightDp = rowHeightDp.takeIf { !it.isNaN() }?.coerceIn(MIN_ROW_HEIGHT_DP, MAX_ROW_HEIGHT_DP)
                    ?: DEFAULT_ROW_HEIGHT_DP,
                showCriticalPath = showCriticalPath,
            )
        }
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
