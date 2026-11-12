package noel.chronova.app.voice.orb

import android.opengl.Matrix
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.FilamentAsset
import kotlin.math.max

/**
 * One orb drawn into one [TextureView], on a transparent background so it sits
 * on whatever is behind it. Call [frame] from the main thread once per display
 * frame while it is visible, and [destroy] when the view goes away.
 */
class OrbSurface(private val orb: OrbEngine, textureView: TextureView) {
    private val engine = orb.engine
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val lights = IntArray(3)
    private val asset: FilamentAsset
    private val center = FloatArray(3)
    private var unitScale = 1f
    private val transform = FloatArray(16)
    private var swapChain: SwapChain? = null
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)

    init {
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply { enabled = true; sampleCount = 4 }
        view.bloomOptions = view.bloomOptions.apply { enabled = true; strength = 0.22f }
        camera.setExposure(16f, 1f / 125f, 100f)
        camera.lookAt(0.0, 0.0, 4.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)

        scene.indirectLight = orb.indirectLight
        // Cyan from the lower left, magenta from the upper right, a white key light from above:
        // the reference image's colour sweep, which the texture alone can't carry.
        addLight(0, floatArrayOf(0.37f, 0.82f, 1f), 128_000f, floatArrayOf(2f, 1.2f, -2.5f))
        addLight(1, floatArrayOf(1f, 0.37f, 0.85f), 104_000f, floatArrayOf(-2.2f, -1.6f, -1.2f))
        addLight(2, floatArrayOf(1f, 1f, 1f), 48_000f, floatArrayOf(1.5f, -2f, -3f))

        asset = orb.assetLoader.createAsset(orb.model.duplicate()) ?: error("The orb model didn't load")
        orb.resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        val box = asset.boundingBox
        box.center.copyInto(center)
        unitScale = 1f / max(box.halfExtent[0], max(box.halfExtent[1], box.halfExtent[2]))

        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                camera.setProjection(30.0, width.toDouble() / height.coerceAtLeast(1), 0.1, 20.0, Camera.Fov.VERTICAL)
            }
        }
        uiHelper.attachTo(textureView)
    }

    private fun addLight(slot: Int, color: FloatArray, lux: Float, direction: FloatArray) {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(color[0], color[1], color[2])
            .intensity(lux)
            .direction(direction[0], direction[1], direction[2])
            .castShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)
        lights[slot] = entity
    }

    /** Draws one frame in [pose]; does nothing until the surface is ready. */
    fun frame(pose: OrbPose, frameTimeNanos: Long) {
        val chain = swapChain ?: return
        if (!uiHelper.isReadyToRender) return
        Matrix.setIdentityM(transform, 0)
        Matrix.rotateM(transform, 0, pose.pitchDegrees, 1f, 0f, 0f)
        Matrix.rotateM(transform, 0, pose.yawDegrees, 0f, 1f, 0f)
        val s = unitScale * pose.scale
        Matrix.scaleM(transform, 0, s, s, s)
        Matrix.translateM(transform, 0, -center[0], -center[1], -center[2])
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(asset.root), transform)
        val glow = 0.8f * pose.glow
        asset.instance.materialInstances.forEach { it.setParameter("emissiveFactor", glow, glow, glow) }
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    fun destroy() {
        uiHelper.detach()
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null
        scene.removeEntities(asset.entities)
        orb.assetLoader.destroyAsset(asset)
        lights.forEach {
            engine.destroyEntity(it)
            EntityManager.get().destroy(it)
        }
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyRenderer(renderer)
    }
}
