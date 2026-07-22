package com.cineshot.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cineshot.app.ui.theme.FilmBlack
import com.cineshot.app.ui.theme.FilmCream
import com.cineshot.app.ui.theme.FilmAmber
import com.cineshot.app.ui.theme.FilmTan
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Boot splash — a film-projector startup sequence with
 * a hand-written movie quote and iris-open transition.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    val irisRadius by animateFloatAsState(
        targetValue = if (reveal) 1500f else 0f,
        animationSpec = tween(800),
        label = "irisOpen"
    )

    LaunchedEffect(Unit) {
        delay(600)     // hold black
        reveal = true
        delay(2800)    // show quote
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FilmBlack),
        contentAlignment = Alignment.Center
    ) {
        // Projector beam / iris
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Vignette mask (inverted iris)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, FilmBlack),
                    center = center,
                    radius = irisRadius
                ),
                radius = size.maxDimension,
                center = center
            )

            // Projector light cone
            if (reveal) {
                val beamAlpha = (irisRadius / 800f).coerceIn(0f, 0.25f)
                val path = Path().apply {
                    moveTo(size.width * 0.5f, -20f)
                    lineTo(size.width * 0.15f, size.height * 0.85f)
                    lineTo(size.width * 0.85f, size.height * 0.85f)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            FilmAmber.copy(alpha = beamAlpha * 0.5f),
                            Color.Transparent
                        )
                    )
                )
            }

            // Subtle sprocket holes scrolling (simplified)
            if (reveal) {
                val holesY = (irisRadius / 10f) % 40f
                var y = -40f
                while (y <= size.height + 40f) {
                    drawCircle(
                        color = FilmTan.copy(alpha = 0.15f),
                        radius = 5f,
                        center = Offset(20f, y + holesY)
                    )
                    drawCircle(
                        color = FilmTan.copy(alpha = 0.15f),
                        radius = 5f,
                        center = Offset(size.width - 20f, y + holesY)
                    )
                    y += 40f
                }
            }
        }

        // Quote text
        if (reveal) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "「电影是每秒二十四格的真理」",
                    style = TextStyle(
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.Normal,
                        fontSize = 22.sp,
                        color = FilmCream.copy(alpha = 0.85f),
                        letterSpacing = 2.sp
                    )
                )
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = "CineShot",
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = FilmCream,
                        letterSpacing = 6.sp
                    )
                )
            }
        }
    }
}
