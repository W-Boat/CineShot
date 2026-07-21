package com.cineshot.app.engine

/**
 * Describes a single cinematic camera move.
 *
 * Keyframes are expressed in the same coordinate space as
 * [com.cineshot.app.gl.VirtualViewport]:
 *   - offsetX / offsetY  in UV-space (normalised; 0 = centre, ±0.1 = 10% pan)
 *   - scale              multiplicative zoom (1.0 = native)
 *   - roll               radians
 *
 * @param startViewport   Viewport state at t = 0.  If null, the move starts from
 *                        whatever the current viewport is when executed.
 * @param endViewport     Target viewport state at t = 1.
 * @param durationMs      Duration of the move in milliseconds.
 * @param easing          Easing curve applied to the progress.
 * @param label           Human-readable name for the UI.
 */
data class CinematicMove(
    val startViewport: CinematicKeyframe?,
    val endViewport: CinematicKeyframe,
    val durationMs: Long,
    val easing: EasingCurve = EasingCurve.EaseInOut,
    val label: String
)
