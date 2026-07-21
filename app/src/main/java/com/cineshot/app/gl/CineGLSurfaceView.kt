package com.cineshot.app.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * GLSurfaceView configured for camera preview at high frame rate.
 *
 * - EGL 2.0 context
 * - Continuous render mode (60fps target)
 * - Delegates rendering to [CineGLRenderer]
 */
class CineGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val cineRenderer = CineGLRenderer()

    var virtualViewport: VirtualViewport
        get() = cineRenderer.virtualViewport
        set(value) { cineRenderer.virtualViewport = value }

    /** Register this so CameraX can bind to the OES texture once GL is ready. */
    var onCameraTextureReady: ((texId: Int, surfaceTexture: android.graphics.SurfaceTexture) -> Unit)?
        get() = cineRenderer.onCameraTextureReady
        set(value) { cineRenderer.onCameraTextureReady = value }

    init {
        setEGLContextClientVersion(2)
        setRenderer(cineRenderer)
        // Continuous render for 60fps preview
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
