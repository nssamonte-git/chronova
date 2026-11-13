package noel.chronova.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import noel.chronova.core.domain.appearance.AppearanceRepository
import noel.chronova.core.model.ThemeMode

/** In-memory appearance choices for tests. */
class FakeAppearanceRepository(initial: ThemeMode = ThemeMode.SYSTEM) : AppearanceRepository {
    private val mode = MutableStateFlow(initial)
    override val themeMode: StateFlow<ThemeMode> = mode

    override fun setThemeMode(mode: ThemeMode) {
        this.mode.value = mode
    }
}
