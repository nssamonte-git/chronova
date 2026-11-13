package noel.chronova.core.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material's "sync" glyph: two arrows chasing each other round a circle. Defined here because
 * it is only in the large extended icon set. Used for syncing rather than the single-arrow
 * "refresh", whose one arrowhead sits exactly where a count badge goes.
 */
val SyncIcon: ImageVector = materialIcon(name = "Filled.Sync") {
    materialPath {
        moveTo(12f, 4f)
        verticalLineTo(1f)
        lineTo(8f, 5f)
        lineToRelative(4f, 4f)
        verticalLineTo(6f)
        curveToRelative(3.31f, 0f, 6f, 2.69f, 6f, 6f)
        curveToRelative(0f, 1.01f, -0.25f, 1.97f, -0.7f, 2.8f)
        lineToRelative(1.46f, 1.46f)
        curveTo(19.54f, 15.03f, 20f, 13.57f, 20f, 12f)
        curveToRelative(0f, -4.42f, -3.58f, -8f, -8f, -8f)
        close()
        moveTo(12f, 18f)
        curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
        curveToRelative(0f, -1.01f, 0.25f, -1.97f, 0.7f, -2.8f)
        lineTo(5.24f, 7.74f)
        curveTo(4.46f, 8.97f, 4f, 10.43f, 4f, 12f)
        curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
        verticalLineToRelative(3f)
        lineToRelative(4f, -4f)
        lineToRelative(-4f, -4f)
        verticalLineToRelative(3f)
        close()
    }
}
