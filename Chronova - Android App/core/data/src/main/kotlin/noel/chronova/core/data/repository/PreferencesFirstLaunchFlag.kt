package noel.chronova.core.data.repository

import android.content.SharedPreferences

/** [FirstLaunchFlag] kept in the app's preferences. */
class PreferencesFirstLaunchFlag(private val prefs: SharedPreferences) : FirstLaunchFlag {
    override fun isFirst() = !prefs.getBoolean(KEY_SEEDED, false)
    override fun markDone() = prefs.edit().putBoolean(KEY_SEEDED, true).apply()

    private companion object {
        const val KEY_SEEDED = "sample_seeded"
    }
}
