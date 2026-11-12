package noel.chronova.core.timeline

/** A span of time, [startMillis] inclusive to [endMillis] exclusive (epoch millis). */
data class TimeSpan(val startMillis: Long, val endMillis: Long) {
    val durationMillis: Long get() = endMillis - startMillis
}
