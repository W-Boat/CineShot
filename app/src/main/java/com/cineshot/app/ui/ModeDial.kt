package com.cineshot.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cineshot.app.ui.theme.FilmCream
import com.cineshot.app.ui.theme.FilmTan
import com.cineshot.app.ui.theme.FilmBrown
import com.cineshot.app.ui.theme.FilmGray
import com.cineshot.app.ui.theme.FilmMetal
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A tactile mode dial that rotates through cinematic presets.
 *
 * @param presets       List of (label, poeticTag) pairs.
 * @param selectedIndex Currently selected preset.
 * @param onSelect      Called with the new index after snap.
 */
@Composable
fun ModeDial(
    presets: List<PresetEntry>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    var dragAngle by remember { mutableFloatStateOf(0f) }
    var snappedIndex by remember { mutableStateOf(selectedIndex) }
    val animatedAngle by animateFloatAsState(
        targetValue = dragAngle,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "dialRotation"
    )

    val sectorDeg = 360f / presets.size
    val currentAngle = snappedIndex * sectorDeg

    Box(modifier = modifier.height(180.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(presets) {
                    detectDragGestures { change, _ ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = change.position.x - cx
                        val dy = change.position.y - cy
                        val raw = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        dragAngle = raw

                        val normalized = ((raw % 360f) + 360f) % 360f
                        val newIndex = ((normalized / sectorDeg).roundToInt()) % presets.size
                        if (newIndex != snappedIndex) {
                            snappedIndex = newIndex
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(newIndex)
                        }
                        change.consume()
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val dialRadius = size.minDimension / 2f - 16f

            // Outer ring (metal)
            drawCircle(
                color = FilmBrown,
                radius = dialRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 8f)
            )
            // Inner ring
            drawCircle(
                color = FilmGray,
                radius = dialRadius - 10f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )

            // Tick marks & labels
            rotate(animatedAngle - currentAngle, pivot = Offset(cx, cy)) {
                for (i in presets.indices) {
                    val theta = Math.toRadians((i * sectorDeg).toDouble())
                    val tickInner = dialRadius - 14f
                    val tickOuter = dialRadius - 4f
                    val tx = cx + tickInner * cos(theta).toFloat()
                    val ty = cy + tickInner * sin(theta).toFloat()
                    val t2x = cx + tickOuter * cos(theta).toFloat()
                    val t2y = cy + tickOuter * sin(theta).toFloat()

                    // Tick
                    drawLine(
                        color = if (i == snappedIndex) FilmCream else FilmTan.copy(alpha = 0.4f),
                        start = Offset(tx, ty),
                        end = Offset(t2x, t2y),
                        strokeWidth = if (i == snappedIndex) 3f else 1.5f
                    )

                    // Label
                    val labelRadius = dialRadius - 28f
                    val lx = cx + labelRadius * cos(theta).toFloat()
                    val ly = cy + labelRadius * sin(theta).toFloat()
                    val measured = textMeasurer.measure(
                        presets[i].label,
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 11.sp,
                            color = if (i == snappedIndex) FilmCream else FilmTan.copy(alpha = 0.5f)
                        )
                    )
                    drawText(
                        measured,
                        topLeft = Offset(lx - measured.size.width / 2f, ly - measured.size.height / 2f)
                    )
                }
            }

            // Center indicator
            drawCircle(
                color = FilmMetal,
                radius = 14f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = FilmCream.copy(alpha = 0.6f),
                radius = 6f,
                center = Offset(cx, cy)
            )
        }

        // Selected preset poetic tag
        Text(
            text = presets.getOrNull(snappedIndex)?.poeticTag ?: "",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
                color = FilmTan
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
    }
}

data class PresetEntry(
    val label: String,      // short — fits on dial
    val poeticTag: String   // «慢推·呼吸» style tag
)
