package noel.chronova.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import noel.chronova.app.navigation.ChronovaNavHost
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.model.ThemeMode
import noel.chronova.feature.notifications.presentation.SystemNotifications

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Shows the logo until the first frame, then switches to Theme.Chronova.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ChronovaApp).container
        open = intent?.getStringExtra(SystemNotifications.EXTRA_OPEN)
        openProject = intent?.getStringExtra(EXTRA_OPEN_PROJECT)
        setContent {
            val mode by container.appearance.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The system bars' icons follow the app's theme, not only the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            ChronovaTheme(darkTheme = dark) {
                ChronovaNavHost(
                    container,
                    openNotifications = open == SystemNotifications.OPEN_NOTIFICATIONS,
                    onOpened = { open = null },
                    openProject = openProject,
                    onProjectOpened = { openProject = null },
                )
            }
        }
    }

    /** A tapped notification while the app is open (singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open = intent.getStringExtra(SystemNotifications.EXTRA_OPEN)
        openProject = intent.getStringExtra(EXTRA_OPEN_PROJECT)
    }

    private var open by mutableStateOf<String?>(null)

    /** A tapped task reminder: the project to open. */
    private var openProject by mutableStateOf<String?>(null)

    companion object {
        /** A project id to open (a tapped task reminder). */
        const val EXTRA_OPEN_PROJECT = "open_project"

        // Behind three-button navigation (gesture navigation draws no bar): AndroidX's own defaults.
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
