package com.cineshot.app.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView.Renderer that composites camera frames through a cinematic shader.
 *
 * Pipeline:
 *   CameraX → SurfaceTexture (OES) → fragment shader → screen quad
 *
 * The shader applies [virtualViewport] transforms (pan, zoom, roll) each frame.
 */
class CineGLRenderer : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "CineGLRenderer"

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTextureCoord.xy;
            }
        """

        private const val FRAGMENT_SHADER_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;

            uniform samplerExternalOES uTexture;
            uniform vec2  uOffset;   // pan offset in UV space
            uniform float uScale;    // zoom: 1.0 = native
            uniform float uRoll;     // roll angle in radians

            varying vec2 vTexCoord;

            void main() {
                // Center-based scale
                vec2 uv = (vTexCoord - 0.5) / uScale + 0.5;

                // Roll (2D rotation around center)
                float c = cos(uRoll);
                float s = sin(uRoll);
                uv -= 0.5;
                uv = vec2(uv.x * c - uv.y * s, uv.x * s + uv.y * c);
                uv += 0.5;

                // Pan
                uv += uOffset;

                gl_FragColor = texture2D(uTexture, uv);
            }
        """
    }

    // --- state ---
    private var cameraTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var shader: ShaderProgram? = null
    private var fullQuad: FullScreenQuad? = null
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var frameAvailable = AtomicBoolean(false)

    /** Called from the GL thread when the camera texture + SurfaceTexture are ready. */
    var onCameraTextureReady: ((texId: Int, st: SurfaceTexture) -> Unit)? = null

    /** Virtual-cinema parameters — set from any thread, consumed on draw. */
    @Volatile
    var virtualViewport = VirtualViewport()

    // ------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated")

        // Create the OES texture that will receive camera frames
        cameraTextureId = createOESTexture()

        surfaceTexture = SurfaceTexture(cameraTextureId).apply {
            setOnFrameAvailableListener(
                { frameAvailable.set(true) },
                null // use GL thread handler
            )
        }

        // Compile shader + create full-screen geometry
        shader = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER_OES)
        fullQuad = FullScreenQuad()

        // Wire default texture unit
        shader!!.use()
        shader!!.setUniform1i("uTexture", 0)

        GLES20.glClearColor(0f, 0f, 0f, 1f)

        // Notify that camera can start feeding this SurfaceTexture
        surfaceTexture?.let { st ->
            onCameraTextureReady?.invoke(cameraTextureId, st)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged: ${width}x$height")
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Consume the latest camera frame if available (non-blocking)
        if (frameAvailable.getAndSet(false)) {
            surfaceTexture?.updateTexImage()
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val vp = virtualViewport
        val prog = shader ?: return
        val quad = fullQuad ?: return

        prog.use()
        prog.setUniform2f("uOffset", vp.offsetX, vp.offsetY)
        prog.setUniform1f("uScale", vp.scale)
        prog.setUniform1f("uRoll", vp.roll)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)

        quad.draw(prog)
    }

    // ------------------------------------------------------------------
    // Helpers

    private fun createOESTexture(): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val id = texIds[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return id
    }
}
