package noel.chronova.feature.reminders.data

import noel.chronova.feature.reminders.domain.ReminderEngine

/** Implemented by the Application, so the receivers reach the one engine the app wires. */
interface ReminderHost {
    val reminderEngine: ReminderEngine
}
