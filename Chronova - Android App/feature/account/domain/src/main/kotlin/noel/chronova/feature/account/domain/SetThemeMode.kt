package noel.chronova.feature.account.domain

import noel.chronova.core.domain.appearance.AppearanceRepository
import noel.chronova.core.model.ThemeMode

/** System, light or dark, for this device. */
class SetThemeMode(private val appearance: AppearanceRepository) {
    operator fun invoke(mode: ThemeMode) = appearance.setThemeMode(mode)
}
