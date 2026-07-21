package com.cineshot.app.engine

/**
 * A single keyframe in virtual-viewport space.
 *
 * All parameters match [com.cineshot.app.gl.VirtualViewport].
 */
data class CinematicKeyframe(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val roll: Float = 0f
)
