package com.cineshot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cineshot.app.ui.theme.FilmTan
import com.cineshot.app.ui.theme.FilmCream

/**
 * A vertical zoom-ring indicator drawn along the left edge.
 *
 * Tick marks scale with [value] — like a lens barrel marking.
 *
 * @param value  Zoom factor [0.5 .. 2.0]
 */
@Composable
fun ZoomRing(
    value: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .width(16.dp)
            .height(200.dp)
    ) {
        val h = size.height
        val tickCount = 9
        val spacing = h / (tickCount - 1)

        for (i in 0 until tickCount) {
            val y = spacing * i
            val t = i.toFloat() / (tickCount - 1)
            val mappedVal = 0.5f + t * 1.5f  // 0.5 .. 2.0
            val isMajor = i % 2 == 0
            val tickW = if (isMajor) 12f else 6f
            val active = kotlin.math.abs(mappedVal - value) < 0.15f
            val color = if (active) FilmCream else FilmTan.copy(alpha = 0.35f)

            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(tickW, y),
                strokeWidth = if (active) 2.5f else 1.2f
            )
        }

        // Active marker triangle
        val norm = ((value - 0.5f) / 1.5f).coerceIn(0f, 1f)
        val markerY = norm * (h - spacing) + spacing / 2f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(14f, markerY - 5f)
            lineTo(22f, markerY)
            lineTo(14f, markerY + 5f)
            close()
        }
        drawPath(path, color = FilmCream)
    }
}
