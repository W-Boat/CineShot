package com.cineshot.app.camera

import android.graphics.SurfaceTexture
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/**
 * Wraps CameraX preview and pipes frames into an OpenGL SurfaceTexture.
 *
 * The caller provides a [SurfaceTexture] (created on the GL thread); this class
 * wraps it in a [Surface] and binds CameraX to it.
 */
class CameraController(
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraController"

        // Request 4K — CameraX selects the closest available resolution.
        val TARGET_RESOLUTION = Size(3840, 2160)
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var recordingSurface: Surface? = null

    /**
     * Start the camera and bind output to [surfaceTexture].
     *
     * Must be called after the GL context is ready and a valid OES texture
     * has been created.
     */
    fun start(surfaceTexture: SurfaceTexture) {
        Log.d(TAG, "start — target resolution: $TARGET_RESOLUTION")

        // Set the SurfaceTexture buffer to match our target resolution.
        // This determines the actual frame dimensions delivered by CameraX.
        surfaceTexture.setDefaultBufferSize(
            TARGET_RESOLUTION.width,
            TARGET_RESOLUTION.height
        )
        recordingSurface = Surface(surfaceTexture)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(lifecycleOwner as android.content.Context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindPreview()
        }, ContextCompat.getMainExecutor(lifecycleOwner as android.content.Context))
    }

    private fun bindPreview() {
        val provider = cameraProvider ?: return

        preview = Preview.Builder()
            .setTargetResolution(TARGET_RESOLUTION)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            recordingSurface?.let { surface ->
                // Provide the Surface directly — CameraX renders into it.
                preview!!.setSurfaceProvider { request ->
                    request.provideSurface(
                        surface,
                        cameraExecutor
                    ) { result ->
                        // Surface is no longer in use
                    }
                }
            }
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
            Log.d(TAG, "Camera bound to lifecycle")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera", e)
        }
    }

    /** Release camera resources. */
    fun stop() {
        Log.d(TAG, "stop")
        cameraProvider?.unbindAll()
        recordingSurface?.release()
        recordingSurface = null
        cameraExecutor.shutdown()
    }
}
