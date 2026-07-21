package com.cineshot.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cineshot.app.ui.theme.FilmCream
import com.cineshot.app.ui.theme.FilmTan
import com.cineshot.app.ui.theme.FilmAmber

/**
 * Vintage counter display — numbers tick over like an old light-meter needle.
 *
 * @param prefix   Label e.g. "ZOOM"
 * @param value    Floating value to display.
 * @param format   Format string (default "%.2f").
 * @param suffix   Unit suffix e.g. "×"
 */
@Composable
fun ParamDisplay(
    prefix: String,
    value: Float,
    modifier: Modifier = Modifier,
    format: String = "%.2f",
    suffix: String = ""
) {
    val animatedValue = remember { Animatable(value) }

    LaunchedEffect(value) {
        animatedValue.animateTo(value, animationSpec = tween(200))
    }

    Row(modifier = modifier) {
        Text(
            text = prefix,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = FilmTan.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${format.format(animatedValue.value)}$suffix",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = FilmCream,
                letterSpacing = 0.5.sp
            )
        )
    }
}

/**
 * Compact integer counter — like a frame-counter wheel.
 */
@Composable
fun CounterDisplay(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    val animatedValue = remember { Animatable(value.toFloat()) }

    LaunchedEffect(value) {
        animatedValue.animateTo(value.toFloat(), animationSpec = tween(150))
    }

    Row(modifier = modifier) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = FilmTan.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "%04d".format(animatedValue.value.toInt()),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = FilmAmber,
                letterSpacing = 2.sp
            )
        )
    }
}
