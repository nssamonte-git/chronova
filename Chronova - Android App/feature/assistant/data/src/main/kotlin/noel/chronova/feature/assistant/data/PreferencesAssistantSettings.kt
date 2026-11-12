package noel.chronova.feature.assistant.data

import android.content.SharedPreferences
import androidx.core.content.edit
import noel.chronova.feature.assistant.domain.AssistantSettings

class PreferencesAssistantSettings(private val prefs: SharedPreferences) : AssistantSettings {
    override var noticeAccepted: Boolean
        get() = prefs.getBoolean(KEY_NOTICE, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTICE, value) }

    override fun introduced(projectId: String) = projectId in prefs.getStringSet(KEY_INTRODUCED, emptySet()).orEmpty()

    override fun markIntroduced(projectId: String) {
        // A copy: the set getStringSet returns must not be changed in place.
        val projects = prefs.getStringSet(KEY_INTRODUCED, emptySet()).orEmpty() + projectId
        prefs.edit { putStringSet(KEY_INTRODUCED, projects) }
    }

    private companion object {
        const val KEY_NOTICE = "voice_notice_accepted"
        const val KEY_INTRODUCED = "voice_introduced_projects"
    }
}
