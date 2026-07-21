package com.cineshot.app.engine

import android.animation.ValueAnimator
import com.cineshot.app.gl.VirtualViewport

/**
 * Drives [VirtualViewport] parameters over time in response to [CinematicMove] commands.
 *
 * Thread safety: callbacks from [ValueAnimator] arrive on the main thread.
 * The viewport is read from the GL thread via [CineGLRenderer]'s @Volatile field,
 * so concurrent access is safe.
 */
class MotionController(
    private val viewportProvider: () -> VirtualViewport,
    private val viewportConsumer: (VirtualViewport) -> Unit
) {
    private var runningAnimator: ValueAnimator? = null

    /** Whether a move is currently in progress. */
    val isRunning: Boolean get() = runningAnimator?.isRunning == true

    /**
     * Execute [move], cancelling any in-flight move first.
     */
    fun execute(move: CinematicMove) {
        cancel()

        val start = move.startViewport?.toVirtualViewport() ?: viewportProvider()
        val end = move.endViewport.toVirtualViewport()

        runningAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = move.durationMs
            interpolator = move.easing.toInterpolator()

            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val vp = lerp(start, end, t)
                viewportConsumer(vp)
            }

            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Snap to exact end frame
                    viewportConsumer(end)
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })

            start()
        }
    }

    /** Cancel the active move (viewport stays where it is). */
    fun cancel() {
        runningAnimator?.cancel()
        runningAnimator = null
    }

    // -----------------------------------------------------------------
    // Interpolation
    // -----------------------------------------------------------------

    private fun lerp(a: VirtualViewport, b: VirtualViewport, t: Float): VirtualViewport {
        return VirtualViewport(
            offsetX = a.offsetX + (b.offsetX - a.offsetX) * t,
            offsetY = a.offsetY + (b.offsetY - a.offsetY) * t,
            scale   = a.scale   + (b.scale   - a.scale)   * t,
            roll    = a.roll    + (b.roll    - a.roll)    * t
        )
    }
}

private fun CinematicKeyframe.toVirtualViewport() = VirtualViewport(
    offsetX = offsetX,
    offsetY = offsetY,
    scale = scale,
    roll = roll
)
