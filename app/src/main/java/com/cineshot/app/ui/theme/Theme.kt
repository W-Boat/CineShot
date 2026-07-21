package com.cineshot.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FilmColorScheme = darkColorScheme(
    primary = FilmCream,
    onPrimary = FilmBlack,
    secondary = FilmTan,
    onSecondary = FilmBlack,
    tertiary = FilmGreen,
    onTertiary = FilmCream,
    background = FilmBlack,
    onBackground = FilmCream,
    surface = FilmBlack,
    onSurface = FilmCream,
    surfaceVariant = FilmBrown,
    onSurfaceVariant = FilmTan,
    outline = FilmGray
)

@Composable
fun CineShotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FilmColorScheme,
        typography = FilmTypography,
        content = content
    )
}
