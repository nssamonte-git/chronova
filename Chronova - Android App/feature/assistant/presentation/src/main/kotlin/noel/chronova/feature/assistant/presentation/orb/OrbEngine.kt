package noel.chronova.feature.assistant.presentation.orb

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Texture
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.IBLPrefilterContext
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import com.google.android.filament.EntityManager

/**
 * One Filament engine for the whole app, with what every orb shares: the
 * material system, the loaders, the model bytes and the theme environment.
 * Created on the main thread on first use; [get] answers null if this device
 * can't run Filament (no OpenGL ES 3.0, or the native libraries won't load),
 * and the orb falls back to a plain icon.
 *
 * Filament reports some failures by aborting the process (a native panic), which
 * no try/catch sees. So a "starting" mark is saved before creating the engine and
 * cleared after: if the app died in between, later launches of the same app
 * version skip the orb instead of crashing again.
 */
class OrbEngine private constructor(
    val engine: Engine,
    val materials: UbershaderProvider,
    val assetLoader: AssetLoader,
    val resourceLoader: ResourceLoader,
    val model: ByteBuffer,
    val indirectLight: IndirectLight,
) {
    companion object {
        private const val TAG = "OrbEngine"
        private const val MODEL_ASSET = "orb/assistant_orb.glb"
        private var instance: OrbEngine? = null
        private var failed = false

        private const val PREFS = "chronova_orb"
        private const val KEY_STARTING = "starting_version"

        fun get(context: Context): OrbEngine? {
            instance?.let { return it }
            if (failed) return null
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val version = appVersion(app)
            if (prefs.getLong(KEY_STARTING, -1L) == version) {
                Log.w(TAG, "The orb crashed this app version before; showing the plain icon")
                failed = true
                return null
            }
            softwareRenderer()?.let { renderer ->
                // An emulator without a GPU draws with SwiftShader on the CPU. Filament's native
                // code died there mid-render (GitHub's x86 emulator, 15 Nov 2026), past the
                // "starting" guard below, and a CPU-drawn orb is too slow to be worth it anyway.
                Log.w(TAG, "Software renderer ($renderer); showing the plain icon")
                failed = true
                return null
            }
            prefs.edit(commit = true) { putLong(KEY_STARTING, version) }
            return try {
                create(app).also { instance = it }
            } catch (error: Throwable) {
                // UnsatisfiedLinkError, missing GLES 3.0, a broken asset: never crash for the orb.
                Log.w(TAG, "The assistant orb can't render on this device", error)
                failed = true
                null
            } finally {
                if (!failed) prefs.edit(commit = true) { remove(KEY_STARTING) }
            }
        }

        /**
         * The OpenGL ES renderer's name when it draws in software (SwiftShader, llvmpipe), else null.
         * Asks through a throwaway 1x1 EGL context; any EGL failure counts as "not software" and
         * leaves the decision to [create]'s own checks.
         */
        internal fun softwareRenderer(): String? {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)) return null
            var context = EGL14.EGL_NO_CONTEXT
            var surface = EGL14.EGL_NO_SURFACE
            try {
                val configs = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                val wanted = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
                if (!EGL14.eglChooseConfig(display, wanted, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
                context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
                surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
                if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE) return null
                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null
                val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
                return renderer.takeIf { SOFTWARE_RENDERERS.any { name -> it.contains(name, ignoreCase = true) } }
            } catch (error: Throwable) {
                Log.w(TAG, "Couldn't read the OpenGL ES renderer", error)
                return null
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            }
        }

        private val SOFTWARE_RENDERERS = listOf("SwiftShader", "llvmpipe", "softpipe")

        @Suppress("DEPRECATION")
        private fun appVersion(context: Context): Long {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        }

        private fun create(context: Context): OrbEngine {
            Utils.init()
            val engine = Engine.create()
            val materials = UbershaderProvider(engine)
            val assetLoader = AssetLoader(engine, materials, EntityManager.get())
            val resourceLoader = ResourceLoader(engine)
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.flip() }
            return OrbEngine(engine, materials, assetLoader, resourceLoader, model, themeLight(engine))
        }

        /**
         * Reflections in the theme colours, generated instead of shipped: a small
         * equirectangular sky (cyan-blue → violet → magenta, brighter above, with two
         * highlight spots) filtered into a reflection cubemap.
         */
        private fun themeLight(engine: Engine): IndirectLight {
            val width = 256
            val height = 128
            var level = FloatArray(width * height * 3)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    themeSky(x / width.toFloat(), y / height.toFloat()).copyInto(level, (y * width + x) * 3)
                }
            }
            // The filter needs the full mip chain (log2(256) + 1 = 9 levels). They're averaged
            // here on the CPU rather than with generateMipmaps, which needs a renderable float format.
            val levels = 9
            val sky = Texture.Builder()
                .width(width).height(height).levels(levels)
                .sampler(Texture.Sampler.SAMPLER_2D)
                .format(Texture.InternalFormat.R11F_G11F_B10F)
                // The prefilter also regenerates mipmaps itself.
                .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
                .build(engine)
            var w = width
            var h = height
            for (mip in 0 until levels) {
                val pixels = ByteBuffer.allocateDirect(level.size * 4).order(ByteOrder.nativeOrder())
                pixels.asFloatBuffer().put(level)
                sky.setImage(engine, mip, Texture.PixelBufferDescriptor(pixels, Texture.Format.RGB, Texture.Type.FLOAT))
                if (mip < levels - 1) {
                    level = halve(level, w, h)
                    w = (w / 2).coerceAtLeast(1)
                    h = (h / 2).coerceAtLeast(1)
                }
            }
            val context = IBLPrefilterContext(engine)
            val toCubemap = IBLPrefilterContext.EquirectangularToCubemap(context)
            val specular = IBLPrefilterContext.SpecularFilter(context)
            val cubemap = toCubemap.run(sky)
            val reflections = specular.run(cubemap)
            engine.destroyTexture(sky)
            engine.destroyTexture(cubemap)
            toCubemap.destroy()
            specular.destroy()
            context.destroy()
            return IndirectLight.Builder().reflections(reflections).intensity(36_000f).build(engine)
        }

        /** A box-filtered half-size copy of an RGB float image ([w]×[h], each side at least 1). */
        internal fun halve(src: FloatArray, w: Int, h: Int): FloatArray {
            val nw = (w / 2).coerceAtLeast(1)
            val nh = (h / 2).coerceAtLeast(1)
            val out = FloatArray(nw * nh * 3)
            for (y in 0 until nh) for (x in 0 until nw) for (c in 0 until 3) {
                var sum = 0f
                var n = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val sx = (x * 2 + dx).coerceAtMost(w - 1)
                    val sy = (y * 2 + dy).coerceAtMost(h - 1)
                    sum += src[(sy * w + sx) * 3 + c]
                    n++
                }
                out[(y * nw + x) * 3 + c] = sum / n
            }
            return out
        }

        /** Linear RGB of the theme sky at (u, v) in 0..1 (v = 0 is straight up). */
        internal fun themeSky(u: Float, v: Float): FloatArray {
            val stops = arrayOf(
                0f to intArrayOf(40, 140, 255),
                0.35f to intArrayOf(90, 110, 255),
                0.6f to intArrayOf(150, 80, 245),
                0.85f to intArrayOf(235, 90, 220),
                1f to intArrayOf(40, 140, 255),
            )
            val i = stops.indexOfFirst { u <= it.first }.coerceAtLeast(1)
            val (t0, c0) = stops[i - 1]
            val (t1, c1) = stops[i]
            val k = ((u - t0) / (t1 - t0)).coerceIn(0f, 1f)
            val bright = (1f - abs(v - 0.3f) * 2.2f).coerceAtLeast(0f)
            fun spot(cx: Float, cy: Float, r: Float) = exp(-(((u - cx) * 256).pow(2) / (r * r * 4) + ((v - cy) * 128).pow(2) / (r * r)))
            val highlight = spot(70 / 256f, 30 / 128f, 6f) + 0.8f * spot(190 / 256f, 40 / 128f, 5f)
            return FloatArray(3) { ch ->
                val srgb = ((c0[ch] + (c1[ch] - c0[ch]) * k) * (0.35f + bright) / 255f).coerceAtMost(1f)
                srgbToLinear(srgb) + 3f * highlight
            }
        }

        private fun srgbToLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
}
