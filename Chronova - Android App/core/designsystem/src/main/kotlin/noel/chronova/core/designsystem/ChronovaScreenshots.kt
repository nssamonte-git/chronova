package noel.chronova.core.designsystem

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * The variants every screenshot test is taken in: a normal phone, the same phone with text
 * enlarged to 1.5× (to catch clipped or overlapping text), and the phone in dark mode (the
 * theme follows it). Use it with `@PreviewTest`.
 */
@Preview(name = "phone", widthDp = 360, heightDp = 760, showBackground = true)
@Preview(name = "large font", widthDp = 360, heightDp = 760, showBackground = true, fontScale = 1.5f)
@Preview(
    name = "dark",
    widthDp = 360,
    heightDp = 760,
    showBackground = true,
    backgroundColor = 0xFF0B1120,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ChronovaScreenshots
