package com.cineshot.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.cineshot.app.ui.theme.FilmRed
import com.cineshot.app.ui.theme.FilmGray
import com.cineshot.app.ui.theme.FilmMetal
import com.cineshot.app.ui.theme.FilmBlack

/**
 * Vintage mechanical shutter button with press animation and haptic feedback.
 *
 * Outer brass ring + inner red button.  Press scales in with a spring,
 * release bounces back.  A satisfying "click" feel.
 */
@Composable
fun ShutterButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    var scaleTarget by remember { mutableFloatStateOf(1f) }
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f),
        label = "shutterScale"
    )

    Box(
        modifier = modifier
            .size(80.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        scaleTarget = 0.85f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        tryAwaitRelease()
                        pressed = false
                        scaleTarget = 1f
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = size.minDimension / 2f * scale
            val innerR = outerR * 0.72f

            // Outer ring (brass)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(FilmMetal, FilmGray, FilmBlack),
                    center = Offset(cx - outerR * 0.15f, cy - outerR * 0.15f),
                    radius = outerR
                ),
                radius = outerR,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = FilmBlack,
                radius = outerR,
                center = Offset(cx, cy),
                style = Stroke(width = 3f)
            )

            // Inner button (red)
            val redColor = if (isRecording) Color(0xFFFF2222) else FilmRed
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(redColor, redColor.copy(alpha = 0.6f)),
                    center = Offset(cx - innerR * 0.2f, cy - innerR * 0.2f),
                    radius = innerR
                ),
                radius = innerR,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = redColor.copy(alpha = 0.5f),
                radius = innerR * 0.5f,
                center = Offset(cx + innerR * 0.2f, cy + innerR * 0.2f)
            )
        }
    }
}
