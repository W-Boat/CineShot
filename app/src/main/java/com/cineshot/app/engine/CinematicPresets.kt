package com.cineshot.app.engine

/**
 * Curated cinematic-move presets ready to bind to UI buttons.
 *
 * Every move uses [EasingCurve.SMOOTH] (gentle ease-in-out) by default
 * to avoid robotic linear motion; individual presets may override this.
 */
object CinematicPresets {

    // ── Push-in (slow creep towards subject) ──────────────────────────
    val PUSH_IN = CinematicMove(
        startViewport = null,                  // start from current
        endViewport = CinematicKeyframe(
            scale = 1.35f,
            offsetX = 0.02f                    // slight rightward drift
        ),
        durationMs = 2200,
        easing = EasingCurve.DECEL,            // soft landing
        label = "推近"
    )

    // ── Pull-out (reveal context) ─────────────────────────────────────
    val PULL_OUT = CinematicMove(
        startViewport = null,
        endViewport = CinematicKeyframe(
            scale = 0.72f,                     // wide
            offsetY = -0.03f                   // slight upward reveal
        ),
        durationMs = 2500,
        easing = EasingCurve.ACCEL,            // gentle take-off
        label = "拉远"
    )

    // ── Parallax pan (orbital dolly feel) ─────────────────────────────
    val PARALLAX_PAN = CinematicMove(
        startViewport = null,
        endViewport = CinematicKeyframe(
            offsetX = 0.12f,                   // slide right
            offsetY = 0.04f,
            scale = 1.12f,                     // slight push-in
            roll = -0.03f                      // subtle tilt
        ),
        durationMs = 1800,
        easing = EasingCurve.SMOOTH,
        label = "环绕"
    )

    // ── Crane-up (booming rise) ───────────────────────────────────────
    val CRANE_UP = CinematicMove(
        startViewport = null,
        endViewport = CinematicKeyframe(
            offsetY = -0.15f,                  // move up in frame
            scale = 0.90f,                     // widen slightly
            roll = 0.02f                       // gentle counter-tilt
        ),
        durationMs = 2600,
        easing = EasingCurve.DRAMATIC,
        label = "摇臂"
    )

    // ── Quick reset ───────────────────────────────────────────────────
    val RESET = CinematicMove(
        startViewport = null,
        endViewport = CinematicKeyframe(
            offsetX = 0f,
            offsetY = 0f,
            scale = 1f,
            roll = 0f
        ),
        durationMs = 500,
        easing = EasingCurve.SNAP,
        label = "复位"
    )

    /** All presets in display order. */
    val ALL = listOf(PUSH_IN, PULL_OUT, PARALLAX_PAN, CRANE_UP, RESET)
}
