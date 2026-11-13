package noel.chronova.feature.account.domain

import noel.chronova.core.domain.reminders.ReminderSettings
import noel.chronova.core.model.ReminderLead

/** When task reminders come on this device (or off). */
class SetReminderLead(private val settings: ReminderSettings) {
    operator fun invoke(lead: ReminderLead) = settings.setLead(lead)
}
