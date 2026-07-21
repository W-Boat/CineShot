package com.cineshot.app.dolly

import android.animation.ValueAnimator
import android.util.Log
import android.view.animation.LinearInterpolator
import com.cineshot.app.gl.VirtualViewport

/**
 * Dolly Zoom (Hitchcock vertigo effect) controller.
 *
 * **Principle**
 * An artistic [artisticScale] oscillates (simulating a dolly / zoom move).
 * Simultaneously, face-size feedback adjusts [compensationScale] so the
 * subject stays roughly constant-sized while the background warps.
 *
 * **Pipeline**
 *   CameraX ImageAnalysis → ML Kit face size → compensationScale
 *   ValueAnimator          → artisticScale oscillation
 *   actualScale = artisticScale × compensationScale → GL shader
 *
 * @param viewportConsumer  Invoked whenever the merged viewport should be sent
 *                          to the renderer (main-thread safe).
 */
class DollyZoomController(
    private val viewportConsumer: (VirtualViewport) -> Unit
) {
    companion object {
        private const val TAG = "DollyZoomController"

        // Proportional gain for the face-size feedback loop.
        private const val KP = 0.35f

        // Artistic-scale oscillation range for vertigo mode.
        private const val VERTIGO_SCALE_MIN = 1.0f
        private const val VERTIGO_SCALE_MAX = 1.55f
        private const val VERTIGO_PERIOD_MS = 1800L
    }

    // ── State ─────────────────────────────────────────────────────────

    private var referenceFaceArea = 0f
    private var compensationScale = 1f         // driven by face-size error
    @Volatile private var artisticScale = 1f     // driven by vertigo animator
    @Volatile var intensity = 0.8f               // [0 .. 1] slider

    private var vertigoAnimator: ValueAnimator? = null

    /** Whether the vertigo effect is currently running. */
    val isVertigoActive: Boolean get() = vertigoAnimator?.isRunning == true

    // ── Face-size callback ────────────────────────────────────────────

    /**
     * Call from the ImageAnalysis thread each time a face is detected.
     *
     * @param faceWidth   Bounding-box width in image pixels.
     * @param faceHeight  Bounding-box height in image pixels.
     */
    fun onFaceDetected(faceWidth: Float, faceHeight: Float) {
        val area = faceWidth * faceHeight
        if (area <= 0f) return

        // Lock reference on first usable face.
        if (referenceFaceArea == 0f) {
            referenceFaceArea = area
            Log.d(TAG, "Reference face area locked: $referenceFaceArea")
            return
        }

        // Normalised error: positive → face too big → reduce scale.
        val error = (area - referenceFaceArea) / referenceFaceArea
        compensationScale -= KP * error * intensity
        compensationScale = compensationScale.coerceIn(0.3f, 3.0f)

        val art = artisticScale  // @Volatile snapshot
        val actual = art * compensationScale

        viewportConsumer(
            VirtualViewport(scale = actual.coerceIn(0.25f, 3.0f))
        )

        Log.v(TAG, "art=%.3f comp=%.3f actual=%.3f err=%.3f".format(art, compensationScale, actual, error))
    }

    // ── Vertigo toggle ────────────────────────────────────────────────

    /** Start (or restart) the artistic-scale oscillation. */
    fun startVertigo() {
        vertigoAnimator?.cancel()
        vertigoAnimator = ValueAnimator.ofFloat(VERTIGO_SCALE_MIN, VERTIGO_SCALE_MAX).apply {
            duration = VERTIGO_PERIOD_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                artisticScale = anim.animatedValue as Float
            }
            start()
        }
        Log.d(TAG, "Vertigo started")
    }

    /** Stop the oscillation and smoothly return to identity. */
    fun stopVertigo() {
        vertigoAnimator?.cancel()
        vertigoAnimator = null

        // Animate artistic scale back to 1.0 so the image settles.
        val current = artisticScale
        ValueAnimator.ofFloat(current, 1.0f).apply {
            duration = 500L
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                artisticScale = anim.animatedValue as Float
            }
            start()
        }
        // Reset compensation so we don't drift.
        compensationScale = 1f
        Log.d(TAG, "Vertigo stopped")
    }

    // ── Reset ─────────────────────────────────────────────────────────

    /** Forget the reference face and disable all effects. */
    fun reset() {
        vertigoAnimator?.cancel()
        vertigoAnimator = null
        artisticScale = 1f
        compensationScale = 1f
        referenceFaceArea = 0f
        viewportConsumer(VirtualViewport())
        Log.d(TAG, "Reset")
    }

    /** Re-lock reference face to the next detected face. */
    fun recalibrateReference() {
        referenceFaceArea = 0f
    }
}
