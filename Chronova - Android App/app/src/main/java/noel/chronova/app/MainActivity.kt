package noel.chronova.app

import android.os.Bundle
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
        setContent {
            ChronovaTheme {
                ChronovaNavHost(container)
            }
        }
    }
}
