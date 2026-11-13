package noel.chronova.core.domain.appearance

import kotlinx.coroutines.flow.StateFlow
import noel.chronova.core.model.ThemeMode

/** How the app looks on this device (read side). */
interface AppearanceQueries {
    val themeMode: StateFlow<ThemeMode>
}

/** This device's appearance choices; never synced. */
interface AppearanceRepository : AppearanceQueries {
    fun setThemeMode(mode: ThemeMode)
}
