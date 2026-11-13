package noel.chronova.core.model

/** How long before a task (or milestone) starts its reminder comes; [OFF] for none. */
enum class ReminderLead(val beforeMillis: Long?) {
    OFF(null),
    AT_START(0L),
    MINUTES_15(15 * 60_000L),
    HOUR_1(60 * 60_000L),
    DAY_1(24 * 60 * 60_000L),
}
