package noel.chronova.core.domain.reminders

import kotlinx.coroutines.flow.StateFlow
import noel.chronova.core.model.ReminderLead

/** When task reminders come on this device (read side). */
interface ReminderSettingsQueries {
    val lead: StateFlow<ReminderLead>
}

/** This device's reminder choice; never synced. */
interface ReminderSettings : ReminderSettingsQueries {
    fun setLead(lead: ReminderLead)
}
