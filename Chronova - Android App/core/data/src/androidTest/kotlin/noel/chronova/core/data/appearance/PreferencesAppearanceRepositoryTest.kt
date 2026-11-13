package noel.chronova.core.data.appearance

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import noel.chronova.core.model.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The appearance choice in this test app's own preferences file (the installed app's is never touched). */
@RunWith(AndroidJUnit4::class)
class PreferencesAppearanceRepositoryTest {
    private val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("appearance-test", Context.MODE_PRIVATE)

    @Before
    @After
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun lightUntilAChoiceIsMadeAndTheChoiceIsKept() {
        assertEquals(ThemeMode.LIGHT, PreferencesAppearanceRepository(prefs).themeMode.value)
        PreferencesAppearanceRepository(prefs).setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, PreferencesAppearanceRepository(prefs).themeMode.value)
    }

    @Test
    fun aValueFromANewerVersionReadsAsLight() {
        prefs.edit().putString("theme", "SEPIA").commit()
        assertEquals(ThemeMode.LIGHT, PreferencesAppearanceRepository(prefs).themeMode.value)
    }
}
