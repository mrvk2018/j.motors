package com.jmotors.presentation.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import androidx.compose.ui.geometry.Offset
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws the ARCore camera feed and reports the 2D screen position of Чоник's plane anchor.
 */
class ArCameraRenderer(
    private val displayRotation: () -> Int,
    private val onAnchorProjected: (screen: Offset?, anchored: Boolean) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile
    var session: Session? = null

    @Volatile
    var sessionReady: Boolean = false

    private var textureId = -1
    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var chonikAnchor: Anchor? = null

    private val quadPositions: FloatBuffer = directFloats(
        floatArrayOf(
            -1f, -1f,
            +1f, -1f,
            -1f, +1f,
            +1f, +1f,
        ),
    )
    private val quadTexCoords: FloatBuffer = directFloats(
        floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f,
        ),
    )
    private val transformedTexCoords: FloatBuffer = directFloats(FloatArray(8))

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        textureId = createExternalTexture()
        program = createCameraProgram()
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        session?.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val activeSession = session
        if (!sessionReady || activeSession == null || textureId < 0) {
            onAnchorProjected(null, false)
            return
        }
        try {
            synchronized(activeSession) {
                activeSession.setCameraTextureName(textureId)
                activeSession.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
                val frame = activeSession.update()
                if (frame.timestamp == 0L) return
                drawCameraFeed(frame)
                val anchored = ensureHorizontalAnchor(activeSession, frame)
                val screen = chonikAnchor
                    ?.takeIf { it.trackingState == TrackingState.TRACKING }
                    ?.let { projectAnchor(frame.camera, it) }
                onAnchorProjected(screen, anchored && screen != null)
            }
        } catch (_: CameraNotAvailableException) {
            onAnchorProjected(null, false)
        }
    }

    fun releaseAnchor() {
        chonikAnchor?.detach()
        chonikAnchor = null
    }

    private fun ensureHorizontalAnchor(session: Session, frame: Frame): Boolean {
        val existing = chonikAnchor
        if (existing != null && existing.trackingState == TrackingState.TRACKING) {
            return true
        }
        if (existing != null) {
            existing.detach()
            chonikAnchor = null
        }
        if (frame.camera.trackingState != TrackingState.TRACKING) return false
        for (plane in session.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState != TrackingState.TRACKING) continue
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) continue
            val lifted = plane.centerPose.compose(Pose.makeTranslation(0f, 0.18f, 0f))
            chonikAnchor = plane.createAnchor(lifted)
            return true
        }
        return false
    }

    private fun projectAnchor(camera: Camera, anchor: Anchor): Offset? {
        val pose = anchor.pose
        val view = FloatArray(16)
        val projection = FloatArray(16)
        val viewProjection = FloatArray(16)
        val clip = FloatArray(4)
        camera.getViewMatrix(view, 0)
        camera.getProjectionMatrix(projection, 0, 0.1f, 100f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        Matrix.multiplyMV(
            clip,
            0,
            viewProjection,
            0,
            floatArrayOf(pose.tx(), pose.ty(), pose.tz(), 1f),
            0,
        )
        if (clip[3] <= 0f) return null
        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        val ndcZ = clip[2] / clip[3]
        if (ndcZ < -1f || ndcZ > 1f) return null
        val x = (ndcX + 1f) * 0.5f * viewportWidth
        val y = (1f - ndcY) * 0.5f * viewportHeight
        return Offset(x, y)
    }

    private fun drawCameraFeed(frame: Frame) {
        quadTexCoords.position(0)
        transformedTexCoords.position(0)
        frame.transformDisplayUvCoords(quadTexCoords, transformedTexCoords)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        quadPositions.position(0)
        transformedTexCoords.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, quadPositions)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, transformedTexCoords)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glUseProgram(0)
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        return id
    }

    private fun createCameraProgram(): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val created = GLES20.glCreateProgram()
        GLES20.glAttachShader(created, vertex)
        GLES20.glAttachShader(created, fragment)
        GLES20.glLinkProgram(created)
        return created
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun directFloats(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """

        fun rotationOf(displayRotation: Int): Int = when (displayRotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }
}
