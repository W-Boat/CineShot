package com.cineshot.app.gl

/**
 * Virtual viewport parameters for cinematic camera movement simulation.
 *
 * All values are applied to the source texture coordinates in the fragment shader.
 *
 * @param offsetX  Horizontal pan offset, normalized [0..1].  0 = no offset.
 * @param offsetY  Vertical pan offset,   normalized [0..1].  0 = no offset.
 * @param scale    Zoom factor.  1.0 = actual size; >1 = zoom in; <1 = zoom out.
 * @param roll     Rotation angle in radians.  0 = no rotation.
 */
data class VirtualViewport(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val roll: Float = 0f
)
