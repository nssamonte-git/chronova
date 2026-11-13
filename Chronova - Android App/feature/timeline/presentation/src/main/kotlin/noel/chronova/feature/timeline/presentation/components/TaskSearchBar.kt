package noel.chronova.feature.timeline.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import noel.chronova.core.designsystem.toColor
import noel.chronova.core.model.TaskColor
import noel.chronova.feature.timeline.presentation.R
import noel.chronova.feature.timeline.presentation.TaskKind
import noel.chronova.feature.timeline.presentation.TaskSearchUi

/**
 * Search and filter bar under the top bar: a title search, chips for not done /
 * done / milestones and for the four colours, how many tasks match, and Clear.
 * The text field takes focus when the bar opens to be typed in (not in previews, and not when the
 * assistant opened it: the keyboard would cover the matches).
 */
@Composable
fun TaskSearchBar(
    search: TaskSearchUi,
    onQuery: (String) -> Unit,
    onToggleColor: (TaskColor) -> Unit,
    onToggleKind: (TaskKind) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filter = search.filter
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Not in previews and screenshots: a focused field's blinking cursor would make them flaky.
    val preview = LocalInspectionMode.current
    LaunchedEffect(Unit) { if (!preview && search.typing) focus.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_close))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskKind.entries.forEach { kind ->
                    FilterChip(
                        selected = kind in filter.kinds,
                        onClick = { onToggleKind(kind) },
                        label = { Text(kindName(kind)) },
                    )
                }
                TaskColor.entries.forEach { color ->
                    FilterChip(
                        selected = color in filter.colors,
                        onClick = { onToggleColor(color) },
                        label = { Text(colorName(color)) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(color.toColor(), CircleShape)) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.search_count, search.total, search.matches, search.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (filter.isActive) TextButton(onClick = onClear) { Text(stringResource(R.string.search_clear)) }
            }
        }
    }
}

@Composable
private fun colorName(color: TaskColor): String = stringResource(
    when (color) {
        TaskColor.BLUE -> R.string.color_blue
        TaskColor.GREEN -> R.string.color_green
        TaskColor.PURPLE -> R.string.color_purple
        TaskColor.AMBER -> R.string.color_amber
    },
)

@Composable
private fun kindName(kind: TaskKind): String = stringResource(
    when (kind) {
        TaskKind.NOT_DONE -> R.string.filter_not_done
        TaskKind.DONE -> R.string.filter_done
        TaskKind.MILESTONE -> R.string.filter_milestones
    },
)
