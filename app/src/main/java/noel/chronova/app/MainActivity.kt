package noel.chronova.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import noel.chronova.app.presentation.ChronovaScreen
import noel.chronova.app.ui.theme.ChronovaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChronovaTheme {
                ChronovaScreen()
            }
        }
    }
}
