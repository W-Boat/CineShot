package com.cineshot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cineshot.app.ui.theme.VignetteColor
import com.cineshot.app.ui.theme.LeakWarm
import com.cineshot.app.ui.theme.LeakCool
import com.cineshot.app.ui.theme.FilmTan
import com.cineshot.app.ui.theme.FilmBlack
import kotlin.math.sin
import kotlin.random.Random

/**
 * Viewfinder overlay — film gate, sprocket holes, vignette,
 * grain, and light-leak effects composited on top of the camera preview.
 *
 * @param showGrain   Toggle film-grain noise.
 * @param showLeak    Toggle light-leak warmth.
 */
@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    showGrain: Boolean = true,
    showLeak: Boolean = false,
    aspectRatio: Float = 3f / 4f  // portrait default
) {
    // Stable random seed regenerated on toggle changes
    var grainSeed by remember { mutableStateOf(0L) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // ── Vignette ──────────────────────────────────────────────
        val vignetteRadius = w.coerceAtMost(h) * 1.1f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, VignetteColor),
                center = Offset(w / 2f, h / 2f),
                radius = vignetteRadius
            ),
            radius = vignetteRadius,
            center = Offset(w / 2f, h / 2f)
        )

        // ── Film-gate corner brackets ─────────────────────────────
        val margin = 32f
        val bracketLen = 40f
        val stroke = 2.5f
        drawViewfinderBrackets(margin, bracketLen, stroke)

        // ── Sprocket holes (left & right strips) ──────────────────
        drawSprocketHoles(w, h, left = true)
        drawSprocketHoles(w, h, left = false)

        // ── Light leaks (optional) ────────────────────────────────
        if (showLeak) {
            drawLightLeaks(w, h, grainSeed)
        }

        // ── Film grain (optional) ─────────────────────────────────
        if (showGrain) {
            drawFilmGrain(w, h, grainSeed)
        }
    }
}

// ── Drawing helpers ───────────────────────────────────────────────────

private fun DrawScope.drawViewfinderBrackets(
    margin: Float,
    len: Float,
    stroke: Float
) {
    val w = size.width
    val h = size.height
    val color = FilmTan.copy(alpha = 0.5f)

    // Top-left
    drawLine(color, Offset(margin, margin), Offset(margin + len, margin), stroke)
    drawLine(color, Offset(margin, margin), Offset(margin, margin + len), stroke)
    // Top-right
    drawLine(color, Offset(w - margin, margin), Offset(w - margin - len, margin), stroke)
    drawLine(color, Offset(w - margin, margin), Offset(w - margin, margin + len), stroke)
    // Bottom-left
    drawLine(color, Offset(margin, h - margin), Offset(margin + len, h - margin), stroke)
    drawLine(color, Offset(margin, h - margin), Offset(margin, h - margin - len), stroke)
    // Bottom-right
    drawLine(color, Offset(w - margin, h - margin), Offset(w - margin - len, h - margin), stroke)
    drawLine(color, Offset(w - margin, h - margin), Offset(w - margin, h - margin - len), stroke)
}

private fun DrawScope.drawSprocketHoles(w: Float, h: Float, left: Boolean) {
    val holeW = 8f
    val holeH = 14f
    val spacing = 32f
    val x = if (left) 10f else w - 10f - holeW
    val color = FilmBlack.copy(alpha = 0.55f)
    var y = 20f
    while (y < h - 20f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(holeW, holeH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
        )
        y += spacing
    }
}

private fun DrawScope.drawLightLeaks(w: Float, h: Float, seed: Long) {
    val rng = Random(seed + 7)
    repeat(3) {
        val cx = rng.nextFloat() * w
        val cy = rng.nextFloat() * h * 0.4f  // bias toward top
        val radius = rng.nextFloat() * w * 0.5f + 100f
        val color = if (rng.nextBoolean()) LeakWarm else LeakCool
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = Offset(cx, cy),
                radius = radius
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
    }
}

private fun DrawScope.drawFilmGrain(w: Float, h: Float, seed: Long) {
    val rng = Random(seed + 13)
    val grainCount = (w * h * 0.004f).toInt().coerceIn(200, 2000)
    val grainColor = FilmBlack.copy(alpha = 0.12f)
    repeat(grainCount) {
        val x = rng.nextFloat() * w
        val y = rng.nextFloat() * h
        val radius = rng.nextFloat() * 1.8f + 0.3f
        drawCircle(grainColor, radius, Offset(x, y))
    }
}
