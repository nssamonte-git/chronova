package noel.chronova.app.presentation.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import java.time.ZoneId

/**
 * The voice assistant as a screen that hosts it (the timeline) sees it. The screen knows
 * only this contract; the app shell builds it from the assistant feature, so neither
 * feature depends on the other.
 */
@Immutable
class AssistantSlot(
    /** False when this build or account can't use the assistant: no orb is shown. */
    val available: Boolean,
    /** Tasks to highlight (the ones it just changed). */
    val highlighted: Set<String>,
    /** A message for the screen's snackbar, or null. */
    val snackbar: AssistantSnackbar?,
    /** Runs for the whole screen: stops it when the user leaves the app, holds the orientation while busy. */
    val screenEffects: @Composable () -> Unit,
    /** The small orb that opens it (the screen places it above "Add task"). */
    val orbButton: @Composable (Modifier) -> Unit,
    /** Drawn over the screen once its content is ready: the big orb, the one-time notice, the introduction. */
    val overlay: @Composable (zone: ZoneId) -> Unit,
) {
    companion object {
        /** No assistant (previews, tests). */
        val None = AssistantSlot(false, emptySet(), null, {}, {}, {})
    }
}

/**
 * One snackbar message. [key] identifies the message, so a screen shows it once however
 * often it recomposes; the screen calls [onResult] when it has been shown.
 */
@Immutable
class AssistantSnackbar(
    val key: Any,
    val text: String,
    val actionLabel: String?,
    val long: Boolean,
    val onResult: (actionPerformed: Boolean) -> Unit,
)
