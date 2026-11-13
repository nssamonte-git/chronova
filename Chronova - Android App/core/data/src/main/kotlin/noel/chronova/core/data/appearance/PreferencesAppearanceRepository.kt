package noel.chronova.core.data.appearance

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import noel.chronova.core.domain.appearance.AppearanceRepository
import noel.chronova.core.model.ThemeMode

/**
 * Appearance choices in their own preferences file, so they belong to the device and
 * survive signing out (which clears the account's data). An unknown stored value
 * (from a newer version) reads as following the phone.
 */
class PreferencesAppearanceRepository(private val prefs: SharedPreferences) : AppearanceRepository {
    private val mode = MutableStateFlow(read())

    override val themeMode: StateFlow<ThemeMode> = mode.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        this.mode.value = mode
    }

    private fun read(): ThemeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM

    private companion object {
        const val KEY_THEME = "theme"
    }
}
