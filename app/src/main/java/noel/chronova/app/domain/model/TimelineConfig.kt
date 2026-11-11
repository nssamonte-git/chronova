package noel.chronova.app.domain.model

/** Unit labelled on the time axis; [minutes] is the length of one unit. */
enum class TimeScale(val minutes: Long) {
    HOURS(60),
    DAYS(24 * 60),
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
    val unitWidthDp: Float = DEFAULT_HOUR_WIDTH_DP,
    val snapMinutes: Int = 15,
    val rowHeightDp: Float = DEFAULT_ROW_HEIGHT_DP,
) {
    companion object {
        const val DEFAULT_HOUR_WIDTH_DP = 96f
        const val DEFAULT_DAY_WIDTH_DP = 480f
        const val DEFAULT_ROW_HEIGHT_DP = 56f
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
