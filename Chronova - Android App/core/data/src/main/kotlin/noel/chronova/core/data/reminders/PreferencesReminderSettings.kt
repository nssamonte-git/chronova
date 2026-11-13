package noel.chronova.core.data.reminders

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import noel.chronova.core.domain.reminders.ReminderSettings
import noel.chronova.core.model.ReminderLead

/** The reminder choice in its own preferences file (kept on the device, and when signing out); off unless chosen. */
class PreferencesReminderSettings(private val prefs: SharedPreferences) : ReminderSettings {
    private val current = MutableStateFlow(read())

    override val lead: StateFlow<ReminderLead> = current.asStateFlow()

    override fun setLead(lead: ReminderLead) {
        prefs.edit().putString(KEY_LEAD, lead.name).apply()
        current.value = lead
    }

    private fun read(): ReminderLead = ReminderLead.entries.firstOrNull { it.name == prefs.getString(KEY_LEAD, null) } ?: ReminderLead.OFF

    private companion object {
        const val KEY_LEAD = "lead"
    }
}
