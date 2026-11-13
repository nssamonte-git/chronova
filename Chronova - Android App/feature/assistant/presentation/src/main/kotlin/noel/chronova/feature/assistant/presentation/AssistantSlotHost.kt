package noel.chronova.feature.assistant.presentation

import noel.chronova.core.ui.assistant.AssistantViewRequest
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import noel.chronova.core.ui.assistant.AssistantSlot
import noel.chronova.core.ui.assistant.AssistantSnackbar
import noel.chronova.feature.assistant.presentation.orb.OrbMode
import noel.chronova.core.designsystem.R as DesignR
import noel.chronova.core.ui.R as CoreUiR

/**
 * The assistant as the timeline hosts it ([AssistantSlot]): tapping the orb shows the
 * one-time notice, then asks for the microphone, then opens the big orb and listens.
 */
@Composable
fun rememberAssistantSlot(assistant: AssistantViewModel): AssistantSlot {
    val state by assistant.state.collectAsStateWithLifecycle()
    val message by assistant.message.collectAsStateWithLifecycle()
    val highlighted by assistant.highlight.collectAsStateWithLifecycle()
    val busy by assistant.busy.collectAsStateWithLifecycle()
    val view by assistant.view.collectAsStateWithLifecycle()
    val micLevel = assistant.level
    val context = LocalContext.current
    val resources = LocalResources.current
    var showNotice by rememberSaveable { mutableStateOf(false) }
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) assistant.open() else assistant.onMicrophoneDenied()
    }
    val startAssistant = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            assistant.open()
        } else {
            askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val onOrbTap = { if (state.noticeAccepted) startAssistant() else showNotice = true }
    val openAppSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
    // A tap the server didn't answer: a toast, and the orb stays closed.
    LaunchedEffect(message) {
        if (message == AssistantMessage.ServerUnreachable) {
            Toast.makeText(context, R.string.assistant_cant_reach_server, Toast.LENGTH_SHORT).show()
            assistant.dismissMessage()
        }
    }
    val undoLabel = stringResource(CoreUiR.string.undo)
    val snackbar = message?.let { shown ->
        val done: (Boolean) -> Unit = { assistant.dismissMessage() }
        when (shown) {
            is AssistantMessage.Applied -> AssistantSnackbar(
                key = shown,
                text = resources.getQuantityString(R.plurals.assistant_applied, shown.taskCount, shown.taskCount),
                actionLabel = undoLabel,
                long = true,
                onResult = { undo -> if (undo) assistant.undo() else assistant.dismissMessage() },
            )
            AssistantMessage.UndoNotPossible -> AssistantSnackbar(shown, resources.getString(CoreUiR.string.undo_not_possible), null, false, done)
            AssistantMessage.NeedsConnection -> AssistantSnackbar(shown, resources.getString(R.string.assistant_needs_connection), null, false, done)
            AssistantMessage.NeedsSignIn -> AssistantSnackbar(shown, resources.getString(R.string.assistant_needs_sign_in), null, false, done)
            AssistantMessage.MicrophoneDenied -> AssistantSnackbar(
                key = shown,
                text = resources.getString(R.string.assistant_mic_denied),
                actionLabel = resources.getString(R.string.assistant_open_settings),
                long = true,
                onResult = { open ->
                    if (open) openAppSettings()
                    assistant.dismissMessage()
                },
            )
            AssistantMessage.ServerUnreachable -> null // shown as a toast
        }
    }
    return AssistantSlot(
        available = state.availability != AssistantAvailability.NOT_CONFIGURED,
        highlighted = highlighted,
        snackbar = snackbar,
        viewRequest = view?.let { request -> AssistantViewRequest(request.id, request.action) { assistant.viewShown(request.id) } },
        reportView = assistant::onView,
        screenEffects = {
            // Leaving the app stops the assistant; turning the phone (the activity is only recreated) doesn't.
            val activity = LocalActivity.current
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                if (activity?.isChangingConfigurations != true) assistant.onLeftApp()
            }
            // While the assistant listens, talks or applies changes, turning the phone doesn't turn the screen.
            HoldOrientation(activity, hold = busy)
        },
        orbButton = { modifier ->
            val assistantLabel = stringResource(R.string.assistant_open)
            // The live 3D orb; the plain mic icon if this device can't render it. Paused while the big orb is open.
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOrbTap)
                    .semantics(mergeDescendants = true) { contentDescription = assistantLabel },
            ) {
                AssistantOrb(mode = OrbMode.Idle, active = !state.open, modifier = Modifier.size(60.dp)) {
                    Icon(painterResource(DesignR.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
        },
        overlay = { zone ->
            // First open of this project: the assistant introduces itself (once it can be heard).
            LaunchedEffect(state.availability) {
                if (state.availability == AssistantAvailability.READY) assistant.introduceIfFirstVisit()
            }
            AssistantOverlay(state = state, zone = zone, level = { micLevel.value }, onClose = assistant::close)
            if (showNotice) {
                AssistantNotice(
                    onContinue = {
                        showNotice = false
                        assistant.acceptNotice()
                        startAssistant()
                    },
                    onDismiss = { showNotice = false },
                )
            }
        },
    )
}

/**
 * Keeps [activity] in the orientation it is in now while [hold] is true, and gives back what it asked
 * for before once [hold] is false (the screen then follows the phone again).
 */
@Composable
private fun HoldOrientation(activity: Activity?, hold: Boolean) {
    DisposableEffect(activity, hold) {
        if (activity == null || !hold) return@DisposableEffect onDispose {}
        val before = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose { activity.requestedOrientation = before }
    }
}
