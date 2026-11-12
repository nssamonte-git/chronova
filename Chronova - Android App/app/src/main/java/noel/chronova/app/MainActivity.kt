package noel.chronova.app

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import noel.chronova.app.notifications.SystemNotifications
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import noel.chronova.app.navigation.ChronovaNavHost
import noel.chronova.app.ui.theme.ChronovaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Shows the logo until the first frame, then switches to Theme.Chronova.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ChronovaApp).container
        open = intent?.getStringExtra(SystemNotifications.EXTRA_OPEN)
        setContent {
            ChronovaTheme {
                ChronovaNavHost(
                    container,
                    openNotifications = open == SystemNotifications.OPEN_NOTIFICATIONS,
                    onOpened = { open = null },
                )
            }
        }
    }

    /** A tapped notification while the app is open (singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open = intent.getStringExtra(SystemNotifications.EXTRA_OPEN)
    }

    private var open by mutableStateOf<String?>(null)
}
