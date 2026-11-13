package noel.chronova.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import noel.chronova.core.domain.reminders.ReminderSettings
import noel.chronova.core.model.ReminderLead

/** In-memory reminder choice for tests. */
class FakeReminderSettings(initial: ReminderLead = ReminderLead.OFF) : ReminderSettings {
    private val current = MutableStateFlow(initial)
    override val lead: StateFlow<ReminderLead> = current

    override fun setLead(lead: ReminderLead) {
        current.value = lead
    }
}
