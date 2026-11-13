package noel.chronova.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import noel.chronova.core.designsystem.R as DesignR
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.ui.R

/**
 * The top bar every screen uses: it sits on the page (no band of its own), the title is the
 * theme's title style, and the actions are quiet until one is marked active by its caller.
 * [title] is a slot for the project list's brand header; most screens pass [ChronovaTopBarTitle].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronovaTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** What the back arrow says to a screen reader; "Back" when null. */
    backDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backDescription ?: stringResource(R.string.back),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.background,
            scrolledContainerColor = colors.background,
            navigationIconContentColor = colors.onSurface,
            titleContentColor = colors.onSurface,
            actionIconContentColor = colors.onSurfaceVariant,
        ),
    )
}

/** A screen's name in the top bar: one line, ending in "…" when it doesn't fit. */
@Composable
fun ChronovaTopBarTitle(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * The logo's three bars, in its gradient: the app's small decoration for empty pages. Purely
 * decorative, so it has no description.
 */
@Composable
fun AuroraBars(modifier: Modifier = Modifier) {
    val brush = ChronovaTheme.extended.auroraHorizontal
    Canvas(modifier) {
        val bar = size.height / 5f
        val radius = CornerRadius(bar / 2f)
        // Start and length of each bar, as shares of the width: staggered like a small schedule.
        listOf(0f to 0.55f, 0.2f to 0.5f, 0.38f to 0.62f).forEachIndexed { row, (start, length) ->
            drawRoundRect(
                brush = brush,
                topLeft = Offset(size.width * start, bar * 2f * row),
                size = Size(size.width * length, bar),
                cornerRadius = radius,
            )
        }
    }
}

/** A small label above a group of controls ("Appearance", "Task reminders"). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 4.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The app's name with its logo and tagline: the project list's title, in place of a plain
 * screen name. The logo is decoration here; the name beside it says what it is.
 */
@Composable
fun BrandTitle(tagline: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            painterResource(DesignR.drawable.brand_splash_logo),
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                tagline,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
