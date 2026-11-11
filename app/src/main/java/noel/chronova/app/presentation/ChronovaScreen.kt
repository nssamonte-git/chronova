package noel.chronova.app.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.ZoneOffset
import noel.chronova.app.R
import noel.chronova.app.ui.theme.ChronovaTheme

@Composable
fun ChronovaScreen(
    modifier: Modifier = Modifier,
    viewModel: ChronovaViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChronovaContent(state = state, modifier = modifier)
}

@Composable
private fun ChronovaContent(state: ChronovaUiState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ChronovaTopBar() },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        // Temporary task list; replaced by the timeline in Batch 3.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.tasks.forEach { task ->
                Text(
                    text = "${task.title}  ${TimeFormat.range(task.startMillis, task.endMillis, state.zone)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChronovaTopBar() {
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
    )
}

@Preview(showBackground = true)
@Composable
private fun ChronovaContentPreview() {
    ChronovaTheme { ChronovaContent(state = ChronovaUiState.sample(ZoneOffset.UTC)) }
}
