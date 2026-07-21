package com.cineshot.app.engine

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * Easing curve types for cinematic moves.
 *
 * Each variant can produce an Android [Interpolator] used by [MotionController].
 */
sealed class EasingCurve {

    /** Standard ease-in-out cubic (AccelerateDecelerate). */
    data object EaseInOut : EasingCurve()

    /** CSS-style cubic-bezier with two control points. Both x values should be in [0,1]. */
    data class CubicBezier(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float
    ) : EasingCurve()

    // -----------------------------------------------------------------
    // Preset factories (immutable values for cinematic feel)
    // -----------------------------------------------------------------
    companion object {
        /** Gentle ease — slow start, slow end, smooth middle. */
        val SMOOTH = CubicBezier(0.42f, 0.0f, 0.58f, 1.0f)

        /** Slightly decelerating — for push-in where you want a soft landing. */
        val DECEL = CubicBezier(0.0f, 0.0f, 0.2f, 1.0f)

        /** Slightly accelerating — for pull-out where you want a gentle take-off. */
        val ACCEL = CubicBezier(0.4f, 0.0f, 1.0f, 1.0f)

        /** Pronounced ease-in-out for dramatic moves. */
        val DRAMATIC = CubicBezier(0.65f, 0.0f, 0.35f, 1.0f)

        /** Quick snap — almost linear but with a tiny cushion. */
        val SNAP = CubicBezier(0.2f, 0.0f, 0.8f, 1.0f)
    }

    // -----------------------------------------------------------------

    fun toInterpolator(): Interpolator = when (this) {
        is EaseInOut -> android.view.animation.AccelerateDecelerateInterpolator()
        is CubicBezier -> PathInterpolator(x1, y1, x2, y2)
    }
}
