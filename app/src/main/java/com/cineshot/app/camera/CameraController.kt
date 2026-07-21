package com.cineshot.app.camera

import android.graphics.SurfaceTexture
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/**
 * Wraps CameraX preview + ImageAnalysis and pipes frames into an
 * OpenGL SurfaceTexture while also feeding [ImageProxy] frames to
 * an external analyzer (e.g. ML Kit face detection).
 */
class CameraController(
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraController"

        /** Request 4K for the GL pipeline. */
        val TARGET_RESOLUTION = Size(3840, 2160)

        /** Lower resolution for ML Kit analysis — keeps latency minimal. */
        val ANALYSIS_RESOLUTION = Size(640, 480)
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var recordingSurface: Surface? = null

    /** Called on an arbitrary thread with each [ImageProxy] from the camera. */
    var onImageAvailable: ((ImageProxy) -> Unit)? = null

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Start the camera, binding preview output to [surfaceTexture]
     * and analysis frames to [onImageAvailable].
     */
    fun start(surfaceTexture: SurfaceTexture) {
        Log.d(TAG, "start — preview: $TARGET_RESOLUTION  analysis: $ANALYSIS_RESOLUTION")

        surfaceTexture.setDefaultBufferSize(
            TARGET_RESOLUTION.width,
            TARGET_RESOLUTION.height
        )
        recordingSurface = Surface(surfaceTexture)

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(lifecycleOwner as android.content.Context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(lifecycleOwner as android.content.Context))
    }

    fun stop() {
        Log.d(TAG, "stop")
        cameraProvider?.unbindAll()
        recordingSurface?.release()
        recordingSurface = null
        cameraExecutor.shutdown()
        analysisExecutor.shutdown()
    }

    // ── Internals ─────────────────────────────────────────────────────

    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        preview = Preview.Builder()
            .setTargetResolution(TARGET_RESOLUTION)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(ANALYSIS_RESOLUTION)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    onImageAvailable?.invoke(imageProxy)
                    // The consumer MUST close the proxy when done.
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()

            recordingSurface?.let { surface ->
                preview!!.setSurfaceProvider { request ->
                    request.provideSurface(surface, cameraExecutor) { /* released */ }
                }
            }

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            Log.d(TAG, "Use cases bound: Preview + ImageAnalysis")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind use cases", e)
        }
    }
}
