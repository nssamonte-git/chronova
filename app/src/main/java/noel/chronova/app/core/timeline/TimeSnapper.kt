package noel.chronova.app.core.timeline

object TimeSnapper {
    /**
     * Rounds [timeMillis] to the nearest multiple of [intervalMillis], measured from
     * [originMillis] (local midnight, so the grid lines up with clock times).
     *
     * Ties round toward +∞. Uses floorDiv rather than `/` so values before the origin
     * round the same way as values after it (`/` truncates toward zero).
     */
    fun snap(timeMillis: Long, intervalMillis: Long, originMillis: Long = 0L): Long {
        require(intervalMillis > 0) { "Snap interval must be positive" }
        val offset = timeMillis - originMillis
        return originMillis + Math.floorDiv(offset + intervalMillis / 2, intervalMillis) * intervalMillis
    }

    /** The latest grid line at or before [timeMillis]. Used for upper bounds. */
    fun floor(timeMillis: Long, intervalMillis: Long, originMillis: Long = 0L): Long {
        require(intervalMillis > 0) { "Snap interval must be positive" }
        return originMillis + Math.floorDiv(timeMillis - originMillis, intervalMillis) * intervalMillis
    }

    /** The earliest grid line at or after [timeMillis]. Used for lower bounds. */
    fun ceil(timeMillis: Long, intervalMillis: Long, originMillis: Long = 0L): Long {
        require(intervalMillis > 0) { "Snap interval must be positive" }
        return originMillis - Math.floorDiv(originMillis - timeMillis, intervalMillis) * intervalMillis
    }
}
