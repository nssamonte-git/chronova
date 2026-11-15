package noel.chronova.feature.assistant.presentation

import android.provider.Settings
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import noel.chronova.feature.assistant.presentation.orb.OrbEngine
import noel.chronova.feature.assistant.presentation.orb.OrbMode
import kotlinx.coroutines.delay
import noel.chronova.feature.assistant.presentation.orb.OrbMotion
import noel.chronova.feature.assistant.presentation.orb.OrbPose
import noel.chronova.feature.assistant.presentation.orb.OrbSurface

/**
 * The assistant orb: the live 3D model (Filament) with a soft halo behind it and
 * a centre spark on top, both following its glow. It animates only while
 * [active] and the screen is started; with "remove animations" it stays still.
 * [level] is the microphone level (0..1) while listening. If the device can't
 * render it, [fallback] is shown instead.
 */
@Composable
fun AssistantOrb(
    mode: OrbMode,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    level: () -> Float = { 0f },
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Previews and screenshot tests can't run the 3D engine: they get the fallback.
    val inspection = LocalInspectionMode.current
    val orb = remember { if (inspection) null else OrbEngine.get(context) }
    if (orb == null) {
        Box(modifier, contentAlignment = Alignment.Center) { fallback() }
        return
    }
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = active && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    var surface by remember { mutableStateOf<OrbSurface?>(null) }
    val glow = remember { mutableFloatStateOf(1f) }
    val currentMode by rememberUpdatedState(mode)
    val currentLevel by rememberUpdatedState(level)

    LaunchedEffect(surface, running) {
        val target = surface ?: return@LaunchedEffect
        if (!running) return@LaunchedEffect
        val start = withFrameNanos { it }
        var drawn: OrbPose? = null
        while (true) {
            withFrameNanos { now ->
                val pose = OrbMotion.pose(currentMode, (now - start) / 1e9f, currentLevel(), reducedMotion)
                // An unchanged pose draws nothing, so the window isn't redrawn for it.
                if (pose != drawn) {
                    glow.floatValue = pose.glow
                    target.frame(pose, now)
                    drawn = pose
                }
            }
            // Resting or still, the orb draws a few frames a second instead of every one.
            val pause = OrbMotion.frameIntervalMs(currentMode, reducedMotion)
            if (pause > 0) delay(pause)
        }
    }

    Box(
        modifier
            .drawBehind { drawHalo(glow.floatValue) }
            .drawWithContent {
                drawContent()
                drawSpark(glow.floatValue)
            },
    ) {
        AndroidView(
            factory = { viewContext -> TextureView(viewContext).also { surface = OrbSurface(orb, it) } },
            onRelease = {
                surface?.destroy()
                surface = null
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A soft blue-violet glow around the orb, like the light it casts in the reference image. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHalo(glow: Float) {
    val radius = size.minDimension * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFF6A7BFF).copy(alpha = 0.32f * glow.coerceAtMost(1.6f)),
            0.6f to Color(0xFFA83CFB).copy(alpha = 0.14f * glow.coerceAtMost(1.6f)),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
    )
}

/** The bright spark at the hourglass neck and its horizontal flare, added on top (light adds). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpark(glow: Float) {
    val strength = (glow - 0.4f).coerceIn(0f, 1.6f)
    val radius = size.minDimension * 0.17f * (0.8f + 0.2f * strength)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.9f * strength.coerceAtMost(1f)),
            0.18f to Color(0xFFA0E6FF).copy(alpha = 0.55f * strength.coerceAtMost(1f)),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        blendMode = BlendMode.Plus,
    )
    val flareWidth = size.width * 0.56f
    val flareHeight = size.minDimension * 0.022f
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to Color(0xFFC8F0FF).copy(alpha = 0.5f * strength.coerceAtMost(1f)),
            1f to Color.Transparent,
            startX = center.x - flareWidth / 2,
            endX = center.x + flareWidth / 2,
        ),
        topLeft = Offset(center.x - flareWidth / 2, center.y - flareHeight / 2),
        size = Size(flareWidth, flareHeight),
        blendMode = BlendMode.Plus,
    )
}
