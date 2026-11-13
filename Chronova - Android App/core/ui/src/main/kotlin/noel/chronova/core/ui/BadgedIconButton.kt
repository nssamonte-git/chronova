package noel.chronova.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * An icon button with a count badge: hidden at 0, "99+" above 99. Material's `IconButton` clips
 * its content to its 40 dp circle, which crops a wide badge; this draws the same 48 dp touch target,
 * ripple and button role without the clip. The badge stays in the button's semantics, so a screen
 * reader announces it with the button.
 */
@Composable
fun BadgedIconButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 20.dp),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val color = LocalContentColor.current.let { if (enabled) it else it.copy(alpha = DISABLED_ALPHA) }
        CompositionLocalProvider(LocalContentColor provides color) {
            BadgedBox(badge = { if (count > 0) Badge { Text(if (count > 99) "99+" else "$count") } }) { icon() }
        }
    }
}

/** Material's opacity for disabled content. */
private const val DISABLED_ALPHA = 0.38f
