package com.jmotors.presentation.ar.car

import android.opengl.Matrix
import android.util.Log
import android.view.Choreographer
import android.view.TextureView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.IndirectLight
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.VertexBuffer
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val TAG = "JMotors"

/** Seconds for one full Y-axis revolution after materialize. */
private const val CAR_ORBIT_SECONDS = 42f

/** Half IPD in Filament world units after [ModelViewer.transformToUnitCube]. */
private const val STEREO_EYE_X = 0.055f

private const val MICROSCOPIC_SCALE = 0.001f
private const val BURST_SECONDS = 0.5f
private const val COLLAPSE_SECONDS = 1.5f
private const val SCALE_UP_SECONDS = 0.8f
private const val FADE_SECONDS = 0.35f
private const val PARTICLE_COUNT = 64

/** Filament world: unit-cube car sits at (0, 0, -4). Sphere is above that in the same view. */
private const val SPHERE_ORIGIN_Y = 0.95f
private const val SPHERE_ORIGIN_Z = -4.0f
private const val CAR_TARGET_Y = 0.0f
private const val CAR_TARGET_Z = -4.0f

/**
 * Mid-air car slot. SET_CAR materializes the GLB from a Filament particle cloud
 * (true 3D, stereo SBS, depth vs the body), then idles on Y.
 */
@Composable
fun FloatingCarShowcase(
    modelBytes: ByteBuffer?,
    loadFailed: Boolean,
    isLeftEye: Boolean,
    revealNonce: Int = 0,
    modifier: Modifier = Modifier,
) {
    if (modelBytes != null) {
        FilamentCarView(
            modelBytes = modelBytes,
            isLeftEye = isLeftEye,
            revealNonce = revealNonce,
            modifier = modifier,
        )
    } else {
        GlossyFallbackCar(
            isLeftEye = isLeftEye,
            dimmed = !loadFailed,
            modifier = modifier,
        )
    }
}

@Composable
private fun FilamentCarView(
    modelBytes: ByteBuffer?,
    isLeftEye: Boolean,
    revealNonce: Int,
    modifier: Modifier,
) {
    val eyeX = if (isLeftEye) -STEREO_EYE_X else STEREO_EYE_X
    val surfaceHolder = remember(isLeftEye) { arrayOfNulls<FloatingCarSurface>(1) }

    DisposableEffect(isLeftEye) {
        onDispose {
            surfaceHolder[0]?.release()
            surfaceHolder[0] = null
        }
    }

    LaunchedEffect(revealNonce) {
        if (revealNonce <= 0) return@LaunchedEffect
        val deadline = System.currentTimeMillis() + 8_000L
        while (surfaceHolder[0]?.hasLoadedModel() != true && System.currentTimeMillis() < deadline) {
            delay(16)
        }
        surfaceHolder[0]?.beginMaterialize()
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            FloatingCarSurface(context, eyeX).also { created ->
                surfaceHolder[0] = created
                modelBytes?.let { created.submitModel(it) }
            }
        },
        update = { view ->
            surfaceHolder[0] = view
            modelBytes?.let { view.submitModel(it) }
        },
    )
}

/** Fast-out-slow-in with a light overshoot, then settle on 1. */
private fun materializeScale(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    if (x >= 1f) return 1f
    val eased = FastOutSlowInEasing.transform(x)
    val overshoot = 1f + 0.07f * sin((x * PI).toFloat())
    val scaled = MICROSCOPIC_SCALE + (1f - MICROSCOPIC_SCALE) * eased * overshoot
    return if (x > 0.92f) {
        val k = (x - 0.92f) / 0.08f
        scaled * (1f - k) + 1f * k
    } else {
        scaled
    }
}

/**
 * TextureView + Filament [ModelViewer].
 * Model root is [ModelViewer.asset.root]; scale via TransformManager matrices only.
 */
internal class FloatingCarSurface(
    context: android.content.Context,
    private val stereoEyeX: Float,
) : TextureView(context) {

    private var modelViewer: ModelViewer? = null
    private var pendingBytes: ByteBuffer? = null
    private var loadedIdentity: Int = 0
    private var fitMatrix: FloatArray? = null
    private var skybox: Skybox? = null
    private var indirectLight: IndirectLight? = null
    private var particleCloud: FilamentParticleCloud? = null
    private var frameScheduled: Boolean = false
    private var appearanceScale: Float = 1f
    private var orbitPaused: Boolean = false
    private var orbitAnchorNanos: Long = 0L
    private var materializing: Boolean = false
    private var materializeStartNanos: Long = 0L

    private val scratchScale = FloatArray(16)
    private val scratchRot = FloatArray(16)
    private val scratchTmp = FloatArray(16)
    private val scratchOut = FloatArray(16)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val viewer = modelViewer
            if (viewer == null || !isAttachedToWindow) {
                frameScheduled = false
                return
            }
            tickMaterialize(frameTimeNanos)
            applyRootTransform(viewer, frameTimeNanos)
            viewer.render(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        isOpaque = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureViewer()
        pendingBytes?.let { loadIntoViewer(it) }
        scheduleFrames()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        frameScheduled = false
        particleCloud?.destroy()
        particleCloud = null
        modelViewer = null
        fitMatrix = null
        skybox = null
        indirectLight = null
        super.onDetachedFromWindow()
    }

    fun hasLoadedModel(): Boolean = modelViewer?.asset != null && fitMatrix != null

    fun submitModel(buffer: ByteBuffer) {
        val identity = System.identityHashCode(buffer)
        if (identity == loadedIdentity && modelViewer?.asset != null) return
        pendingBytes = buffer
        if (isAttachedToWindow) {
            ensureViewer()
            loadIntoViewer(buffer)
        }
    }

    fun beginMaterialize() {
        val viewer = modelViewer ?: return
        orbitPaused = true
        materializing = true
        materializeStartNanos = System.nanoTime()
        appearanceScale = MICROSCOPIC_SCALE
        setModelShadows(enabled = false)
        particleCloud?.arm()
        applyRootTransform(viewer, materializeStartNanos)
    }

    fun release() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        frameScheduled = false
    }

    private fun tickMaterialize(frameTimeNanos: Long) {
        if (!materializing) return
        val t = (frameTimeNanos - materializeStartNanos) / 1_000_000_000f
        appearanceScale = if (t < BURST_SECONDS) {
            MICROSCOPIC_SCALE
        } else {
            materializeScale(((t - BURST_SECONDS) / SCALE_UP_SECONDS).coerceIn(0f, 1f))
        }
        val fadeStart = BURST_SECONDS + COLLAPSE_SECONDS
        val fade = if (t >= fadeStart) {
            ((t - fadeStart) / FADE_SECONDS).coerceIn(0f, 1f)
        } else {
            0f
        }
        particleCloud?.advance(t, intensity = 1f - fade)
        if (t >= fadeStart + FADE_SECONDS) {
            particleCloud?.hide()
            endMaterialize()
        }
    }

    private fun endMaterialize() {
        if (!materializing) return
        materializing = false
        appearanceScale = 1f
        orbitPaused = false
        orbitAnchorNanos = System.nanoTime()
        setModelShadows(enabled = true)
    }

    private fun modelRoot(viewer: ModelViewer): Int = viewer.asset?.root ?: 0

    private fun ensureViewer() {
        if (modelViewer != null) return
        val manipulator = Manipulator.Builder()
            .targetPosition(0.0f, 0.0f, -4.0f)
            .orbitHomePosition(stereoEyeX, 0.18f, 0.05f)
            .viewport(width.coerceAtLeast(1), height.coerceAtLeast(1))
            .build(Manipulator.Mode.ORBIT)
        val viewer = ModelViewer(this, manipulator = manipulator)
        applyBlackPassthrough(viewer)
        applyStudioLight(viewer)
        particleCloud = FilamentParticleCloud.create(viewer.engine, viewer.scene)
        modelViewer = viewer
    }

    private fun loadIntoViewer(buffer: ByteBuffer) {
        val viewer = modelViewer ?: return
        val copy = buffer.duplicate()
        copy.rewind()
        runCatching {
            viewer.loadModelGlb(copy)
            viewer.transformToUnitCube()
            val root = modelRoot(viewer)
            if (root == 0) return
            val tm = viewer.engine.transformManager
            val instance = tm.getInstance(root)
            val saved = FloatArray(16)
            tm.getTransform(instance, saved)
            fitMatrix = saved
            loadedIdentity = System.identityHashCode(buffer)
            if (orbitPaused || materializing) {
                appearanceScale = MICROSCOPIC_SCALE
                setModelShadows(enabled = false)
            }
        }.onFailure { error ->
            Log.e(TAG, "GLB load failed: ${error.message}", error)
        }
    }

    private fun applyRootTransform(viewer: ModelViewer, frameTimeNanos: Long) {
        val root = modelRoot(viewer)
        if (root == 0) return
        val fit = fitMatrix ?: return
        val yaw = if (orbitPaused) {
            0f
        } else {
            val anchor = if (orbitAnchorNanos == 0L) frameTimeNanos else orbitAnchorNanos
            val seconds = (frameTimeNanos - anchor) / 1_000_000_000.0
            ((seconds / CAR_ORBIT_SECONDS) * 360.0).toFloat()
        }
        val scale = appearanceScale
        Matrix.setIdentityM(scratchScale, 0)
        Matrix.scaleM(scratchScale, 0, scale, scale, scale)
        Matrix.setRotateM(scratchRot, 0, yaw, 0f, 1f, 0f)
        Matrix.multiplyMM(scratchTmp, 0, scratchRot, 0, fit, 0)
        Matrix.multiplyMM(scratchOut, 0, scratchScale, 0, scratchTmp, 0)
        val tm = viewer.engine.transformManager
        tm.setTransform(tm.getInstance(root), scratchOut)
    }

    private fun setModelShadows(enabled: Boolean) {
        val viewer = modelViewer ?: return
        val asset = viewer.asset ?: return
        val rcm = viewer.engine.renderableManager
        for (entity in asset.entities) {
            val ri = rcm.getInstance(entity)
            if (ri != 0) {
                rcm.setCastShadows(ri, enabled)
                rcm.setReceiveShadows(ri, enabled)
            }
        }
    }

    private fun applyBlackPassthrough(viewer: ModelViewer) {
        val engine = viewer.engine
        val black = Skybox.Builder().color(0f, 0f, 0f, 1f).build(engine)
        skybox = black
        viewer.scene.skybox = black
        val ibl = IndirectLight.Builder()
            .irradiance(3, STUDIO_SH)
            .intensity(28_000f)
            .build(engine)
        indirectLight = ibl
        viewer.scene.indirectLight = ibl
        viewer.view.blendMode = com.google.android.filament.View.BlendMode.OPAQUE
        viewer.renderer.clearOptions = com.google.android.filament.Renderer.ClearOptions().apply {
            clear = true
            clearColor[0] = 0f
            clearColor[1] = 0f
            clearColor[2] = 0f
            clearColor[3] = 1f
        }
    }

    private fun applyStudioLight(viewer: ModelViewer) {
        val lights = viewer.engine.lightManager
        val instance = lights.getInstance(viewer.light)
        if (instance != 0) {
            lights.setDirection(instance, 0.42f, -0.62f, -0.66f)
            lights.setIntensity(instance, 95_000f)
        }
    }

    private fun scheduleFrames() {
        if (frameScheduled) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private companion object {
        val STUDIO_SH: FloatArray = floatArrayOf(
            0.78f, 0.80f, 0.86f,
            0.10f, 0.12f, 0.16f,
            0.20f, 0.20f, 0.22f,
            0.07f, 0.07f, 0.09f,
            0.04f, 0.04f, 0.05f,
            0.03f, 0.03f, 0.04f,
            0.02f, 0.02f, 0.03f,
            0.02f, 0.02f, 0.02f,
            0.01f, 0.01f, 0.02f,
        )
    }
}

/**
 * 64 shared-mesh micro-cubes, unlit emissive via gltfio [UbershaderProvider]
 * (already in the APK — no extra native lib). Same seed on both SBS eyes.
 */
private class FilamentParticleCloud private constructor(
    private val engine: Engine,
    private val scene: Scene,
    private val vertexBuffer: VertexBuffer,
    private val indexBuffer: IndexBuffer,
    private val materials: List<MaterialInstance>,
    private val provider: UbershaderProvider,
    private val items: Array<ParticleActor>,
) {
    private val scratch = FloatArray(16)
    private var visible: Boolean = false

    fun arm() {
        respawn()
        if (!visible) {
            for (item in items) {
                scene.addEntity(item.entity)
            }
            visible = true
        }
    }

    fun hide() {
        if (!visible) return
        for (item in items) {
            scene.removeEntity(item.entity)
        }
        visible = false
    }

    fun advance(timeSec: Float, intensity: Float) {
        if (!visible) return
        val tm = engine.transformManager
        val sizeMul = intensity.coerceIn(0f, 1f)
        if (sizeMul <= 0.01f) return
        for (item in items) {
            val (x, y, z) = item.positionAt(timeSec)
            val s = item.baseSize * sizeMul * (0.85f + 0.15f * sin(timeSec * 14f + item.phase))
            Matrix.setIdentityM(scratch, 0)
            Matrix.translateM(scratch, 0, x, y, z)
            Matrix.scaleM(scratch, 0, s, s, s)
            tm.setTransform(tm.getInstance(item.entity), scratch)
        }
    }

    fun destroy() {
        hide()
        val em = EntityManager.get()
        val rcm = engine.renderableManager
        val tm = engine.transformManager
        for (item in items) {
            val ri = rcm.getInstance(item.entity)
            if (ri != 0) rcm.destroy(item.entity)
            val ti = tm.getInstance(item.entity)
            if (ti != 0) tm.destroy(item.entity)
            engine.destroyEntity(item.entity)
            em.destroy(item.entity)
        }
        for (mat in materials) {
            engine.destroyMaterialInstance(mat)
        }
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        runCatching { provider.destroyMaterials() }
        runCatching { provider.destroy() }
    }

    private fun respawn() {
        val rng = Random(STEREO_SEED)
        for (item in items) {
            val z = rng.nextFloat() * 2f - 1f
            val r = sqrt((1f - z * z).coerceAtLeast(0f))
            val theta = rng.nextFloat() * (PI * 2.0).toFloat()
            item.dirX = r * cos(theta)
            item.dirY = r * sin(theta)
            item.dirZ = z
            item.speed = 1.35f + rng.nextFloat() * 1.9f
            item.spiral = 0.18f + rng.nextFloat() * 0.32f
            item.phase = rng.nextFloat() * (PI * 2.0).toFloat()
            item.baseSize = 0.014f + rng.nextFloat() * 0.016f
        }
    }

    companion object {
        private const val STEREO_SEED = 0xC40A1CL

        fun create(engine: Engine, scene: Scene): FilamentParticleCloud? {
            return runCatching {
                val provider = UbershaderProvider(engine)
                val key = MaterialProvider.MaterialKey().apply { unlit = true }
                val uvMap = IntArray(8) { it }
                val cyan = provider.createMaterialInstance(key, uvMap, "particleCyan", null)
                    ?: error("unlit cyan material is null")
                applyGlow(cyan, r = 0.12f, g = 0.95f, b = 1.0f)
                val gold = provider.createMaterialInstance(key, uvMap, "particleGold", null)
                    ?: error("unlit gold material is null")
                applyGlow(gold, r = 1.0f, g = 0.78f, b = 0.18f)
                val mats = listOf(cyan, gold)

                val vb = buildCubeVertexBuffer(engine)
                val ib = buildCubeIndexBuffer(engine)
                val em = EntityManager.get()
                val tm = engine.transformManager
                val actors = Array(PARTICLE_COUNT) { index ->
                    val entity = em.create()
                    RenderableManager.Builder(1)
                        .boundingBox(
                            com.google.android.filament.Box(
                                floatArrayOf(0f, 0f, 0f),
                                floatArrayOf(1f, 1f, 1f),
                            ),
                        )
                        .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
                        .material(0, mats[index % mats.size])
                        .castShadows(false)
                        .receiveShadows(false)
                        .culling(false)
                        .build(engine, entity)
                    tm.create(entity)
                    ParticleActor(entity = entity)
                }
                FilamentParticleCloud(engine, scene, vb, ib, mats, provider, actors)
            }.onFailure { error ->
                Log.e(TAG, "Particle cloud init failed (car still loads): ${error.message}", error)
            }.getOrNull()
        }

        private fun applyGlow(instance: MaterialInstance, r: Float, g: Float, b: Float) {
            runCatching { instance.setParameter("baseColorFactor", r, g, b, 1f) }
            runCatching { instance.setParameter("emissiveFactor", r * 2.4f, g * 2.4f, b * 2.8f) }
            runCatching { instance.setParameter("emissiveStrength", 12f) }
        }
    }
}

private class ParticleActor(
    val entity: Int,
    var dirX: Float = 0f,
    var dirY: Float = 1f,
    var dirZ: Float = 0f,
    var speed: Float = 1.6f,
    var spiral: Float = 0.25f,
    var phase: Float = 0f,
    var baseSize: Float = 0.02f,
) {
    fun positionAt(t: Float): FloatArray {
        val ox = 0f
        val oy = SPHERE_ORIGIN_Y
        val oz = SPHERE_ORIGIN_Z
        val tx = 0f
        val ty = CAR_TARGET_Y
        val tz = CAR_TARGET_Z
        if (t <= BURST_SECONDS) {
            return floatArrayOf(
                ox + dirX * speed * t,
                oy + dirY * speed * t,
                oz + dirZ * speed * t,
            )
        }
        val u = FastOutSlowInEasing.transform(
            ((t - BURST_SECONDS) / COLLAPSE_SECONDS).coerceIn(0f, 1f),
        )
        val bx = ox + dirX * speed * BURST_SECONDS
        val by = oy + dirY * speed * BURST_SECONDS
        val bz = oz + dirZ * speed * BURST_SECONDS
        val cx = bx + (tx - bx) * u
        val cy = by + (ty - by) * u
        val cz = bz + (tz - bz) * u
        val spin = phase + u * 5.8f * PI.toFloat()
        val amp = spiral * (1f - u)
        return floatArrayOf(
            cx + cos(spin) * amp,
            cy + sin(u * PI.toFloat()) * 0.06f * (1f - u),
            cz + sin(spin) * amp,
        )
    }
}

private fun buildCubeVertexBuffer(engine: Engine): VertexBuffer {
    val positions = floatArrayOf(
        -1f, -1f, -1f,
        1f, -1f, -1f,
        1f, 1f, -1f,
        -1f, 1f, -1f,
        -1f, -1f, 1f,
        1f, -1f, 1f,
        1f, 1f, 1f,
        -1f, 1f, 1f,
    )
    val vb = VertexBuffer.Builder()
        .vertexCount(8)
        .bufferCount(1)
        .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
        .build(engine)
    vb.setBufferAt(engine, 0, nativeFloats(positions))
    return vb
}

private fun buildCubeIndexBuffer(engine: Engine): IndexBuffer {
    val indices = shortArrayOf(
        0, 1, 2, 0, 2, 3,
        4, 6, 5, 4, 7, 6,
        0, 4, 5, 0, 5, 1,
        3, 2, 6, 3, 6, 7,
        0, 3, 7, 0, 7, 4,
        1, 5, 6, 1, 6, 2,
    )
    val ib = IndexBuffer.Builder()
        .indexCount(indices.size)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT)
        .build(engine)
    ib.setBuffer(engine, nativeShorts(indices))
    return ib
}

private fun nativeFloats(data: FloatArray): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
    buffer.asFloatBuffer().put(data)
    buffer.rewind()
    return buffer
}

private fun nativeShorts(data: ShortArray): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(data.size * 2).order(ByteOrder.nativeOrder())
    buffer.asShortBuffer().put(data)
    buffer.rewind()
    return buffer
}

@Composable
private fun GlossyFallbackCar(
    isLeftEye: Boolean,
    dimmed: Boolean,
    modifier: Modifier,
) {
    val orbit = rememberInfiniteTransition(label = "fallbackCarOrbit")
    val angle by orbit.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (CAR_ORBIT_SECONDS * 1000).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fallbackYaw",
    )
    val stereo = if (isLeftEye) -0.12f else 0.12f
    val alphaScale = if (dimmed) 0.55f else 1f

    Canvas(modifier = modifier.fillMaxSize()) {
        drawGlossyCar(
            yaw = angle,
            stereoShift = stereo,
            alphaScale = alphaScale,
        )
    }
}

private fun DrawScope.drawGlossyCar(
    yaw: Float,
    stereoShift: Float,
    alphaScale: Float,
) {
    val w = size.width
    val h = size.height
    val scale = min(w, h) * 0.42f
    val cx = w * 0.5f + stereoShift * scale * 0.25f
    val cy = h * 0.58f

    fun pt(x: Float, y: Float, z: Float): Offset {
        val cosA = cos(yaw)
        val sinA = sin(yaw)
        val rx = x * cosA + z * sinA
        val rz = -x * sinA + z * cosA
        val depth = rz + 3.6f
        val f = 2.15f / depth
        return Offset(cx + rx * f * scale, cy - y * f * scale)
    }

    fun quad(a: Offset, b: Offset, c: Offset, d: Offset, color: Color) {
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(c.x, c.y)
            lineTo(d.x, d.y)
            close()
        }
        drawPath(path, color.copy(alpha = color.alpha * alphaScale), style = Fill)
    }

    val paint = Color(0xFFC5CCD4)
    val paintDark = Color(0xFF6E7A86)
    val glass = Color(0xFF9BE7FF)
    val accent = Color(0xFF00F0FF)

    quad(pt(-1.15f, 0.12f, 0.45f), pt(1.05f, 0.12f, 0.45f), pt(1.05f, 0.42f, 0.42f), pt(-1.00f, 0.40f, 0.42f), paint)
    quad(pt(-1.15f, 0.12f, -0.45f), pt(-1.00f, 0.40f, -0.42f), pt(1.05f, 0.42f, -0.42f), pt(1.05f, 0.12f, -0.45f), paintDark)
    quad(pt(-1.00f, 0.40f, 0.42f), pt(1.05f, 0.42f, 0.42f), pt(1.05f, 0.42f, -0.42f), pt(-1.00f, 0.40f, -0.42f), paint.copy(alpha = 0.95f))
    quad(pt(-1.15f, 0.12f, 0.45f), pt(-1.15f, 0.12f, -0.45f), pt(1.05f, 0.12f, -0.45f), pt(1.05f, 0.12f, 0.45f), paintDark.copy(alpha = 0.7f))
    quad(pt(-0.25f, 0.40f, 0.36f), pt(0.55f, 0.42f, 0.34f), pt(0.42f, 0.72f, 0.28f), pt(-0.10f, 0.70f, 0.28f), glass.copy(alpha = 0.55f))
    quad(pt(-0.25f, 0.40f, -0.36f), pt(-0.10f, 0.70f, -0.28f), pt(0.42f, 0.72f, -0.28f), pt(0.55f, 0.42f, -0.34f), glass.copy(alpha = 0.28f))
    quad(pt(-0.10f, 0.70f, 0.28f), pt(0.42f, 0.72f, 0.28f), pt(0.42f, 0.72f, -0.28f), pt(-0.10f, 0.70f, -0.28f), glass.copy(alpha = 0.40f))

    fun wheel(x: Float, z: Float) {
        val hub = pt(x, 0.10f, z)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFEEEEEE).copy(alpha = 0.85f * alphaScale),
                    Color(0xFF22262A).copy(alpha = 0.95f * alphaScale),
                ),
                center = hub,
                radius = scale * 0.12f,
            ),
            radius = scale * 0.11f,
            center = hub,
        )
        drawCircle(
            color = accent.copy(alpha = 0.55f * alphaScale),
            radius = scale * 0.11f,
            center = hub,
            style = Stroke(width = 1.6f),
        )
    }
    wheel(-0.72f, 0.48f)
    wheel(-0.72f, -0.48f)
    wheel(0.72f, 0.48f)
    wheel(0.72f, -0.48f)
    drawCircle(color = accent.copy(alpha = 0.35f * alphaScale), radius = scale * 0.06f, center = pt(1.12f, 0.28f, 0f))
}
