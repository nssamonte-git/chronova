package noel.chronova.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The spacing scale. Screens and sheets take their gutters from here rather than from literals. */
object ChronovaSpacing {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** The side margin of a full screen's content. */
    val screenGutter: Dp = 16.dp

    /** The side and bottom margin inside a bottom sheet or a form page. */
    val sheetGutter: Dp = 24.dp

    /** The gap between two groups of content on a page. */
    val sectionGap: Dp = 24.dp
}
