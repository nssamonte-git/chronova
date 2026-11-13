package noel.chronova.core.designsystem

import androidx.compose.ui.tooling.preview.Preview

/**
 * The variants every screenshot test is taken in: a normal phone, and the same phone with text
 * enlarged to 1.5× (to catch clipped or overlapping text). Use it with `@PreviewTest`.
 */
@Preview(name = "phone", widthDp = 360, heightDp = 760, showBackground = true)
@Preview(name = "large font", widthDp = 360, heightDp = 760, showBackground = true, fontScale = 1.5f)
annotation class ChronovaScreenshots
