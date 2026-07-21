package com.cineshot.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography blending serif titles with monospace readouts —
 * evoking a vintage camera's engraved dials and instruction plates.
 */

// System fonts — no binary downloads needed.
val SerifFamily   = FontFamily.Serif
val MonospaceFamily = FontFamily.Monospace
val CursiveFamily = FontFamily.Cursive   // splash quote

val FilmTypography = Typography(
    // Large title — e.g. "CineShot" on splash
    headlineLarge = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = 2.sp,
        color = FilmCream
    ),
    // Preset labels on the mode dial
    headlineMedium = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 1.5.sp,
        color = FilmCream
    ),
    // Small labels — viewfinder data
    titleSmall = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        color = FilmTan
    ),
    // Parameter readout — counter / meter style
    bodyLarge = TextStyle(
        fontFamily = MonospaceFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 0.5.sp,
        color = FilmCream
    ),
    // Body copy — descriptions
    bodyMedium = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = FilmTan
    ),
    // Small meta
    labelSmall = TextStyle(
        fontFamily = MonospaceFamily,
        fontWeight = FontWeight.Light,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = FilmGray
    )
)
