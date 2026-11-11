package noel.chronova.app.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.ZoneOffset
import noel.chronova.app.R
import noel.chronova.app.domain.model.SchedulerTask
import noel.chronova.app.domain.model.TimeScale
import noel.chronova.app.presentation.components.SchedulerTimeline
import noel.chronova.app.presentation.components.TimelineControls
import noel.chronova.app.ui.theme.ChronovaTheme

@Composable
fun ChronovaScreen(
    modifier: Modifier = Modifier,
    viewModel: ChronovaViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChronovaContent(
        state = state,
        actions = ChronovaActions(
            onSelectTask = viewModel::selectTask,
            onCommitEdit = viewModel::commitEdit,
            onTimeScale = viewModel::setTimeScale,
            onZoomIn = viewModel::zoomIn,
            onZoomOut = viewModel::zoomOut,
            onZoomReset = viewModel::resetZoom,
            onSnap = viewModel::setSnapMinutes,
            onRowHeightStep = viewModel::changeRowHeight,
        ),
        modifier = modifier,
    )
}

/** UI intents, grouped to keep composable signatures short. */
data class ChronovaActions(
    val onSelectTask: (String?) -> Unit = {},
    val onCommitEdit: (SchedulerTask) -> Unit = {},
    val onTimeScale: (TimeScale) -> Unit = {},
    val onZoomIn: () -> Unit = {},
    val onZoomOut: () -> Unit = {},
    val onZoomReset: () -> Unit = {},
    val onSnap: (Int) -> Unit = {},
    val onRowHeightStep: (Int) -> Unit = {},
)

@Composable
private fun ChronovaContent(
    state: ChronovaUiState,
    actions: ChronovaActions,
    modifier: Modifier = Modifier,
) {
    var showControls by rememberSaveable { mutableStateOf(true) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ChronovaTopBar(
                controlsVisible = showControls,
                onToggleControls = { showControls = !showControls },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnimatedVisibility(visible = showControls) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TimelineControls(
                        config = state.config,
                        onTimeScale = actions.onTimeScale,
                        onZoomIn = actions.onZoomIn,
                        onZoomOut = actions.onZoomOut,
                        onZoomReset = actions.onZoomReset,
                        onSnap = actions.onSnap,
                        onRowHeightStep = actions.onRowHeightStep,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SchedulerTimeline(
                state = state,
                onSelectTask = actions.onSelectTask,
                onCommitEdit = actions.onCommitEdit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChronovaTopBar(
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = onToggleControls) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = if (controlsVisible) "Hide timeline settings" else "Show timeline settings",
                    tint = if (controlsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ChronovaContentPreview() {
    ChronovaTheme {
        ChronovaContent(state = ChronovaUiState.sample(ZoneOffset.UTC), actions = ChronovaActions())
    }
}
