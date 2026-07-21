package com.cineshot.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Film-stock palette — warm, desaturated, vintage.
 * No neon, no plastic.  Think Kodak Portra / Fuji Provia.
 */

// ── Primaries ─────────────────────────────────────────────────────────
val FilmBrown    = Color(0xFF3B2A1E)   // dark leather
val FilmTan      = Color(0xFFC4A882)   // aged paper / skin
val FilmCream    = Color(0xFFF5ECD7)   // warm off-white
val FilmGreen    = Color(0xFF2E4A3A)   // forest / lens-coating

// ── Surfaces ──────────────────────────────────────────────────────────
val FilmBlack    = Color(0xFF1A1510)   // deep vignette
val FilmGray     = Color(0xFF5C5346)   // brushed metal
val FilmMetal    = Color(0xFF8A7E6E)   // aged brass

// ── Accents ───────────────────────────────────────────────────────────
val FilmRed      = Color(0xFFC0392B)   // shutter button
val FilmAmber    = Color(0xFFD4A017)   // light-leak / indicator
val FilmRust     = Color(0xFF8B4513)   // rust / patina

// ── Overlay tints ─────────────────────────────────────────────────────
val VignetteColor = FilmBlack.copy(alpha = 0.35f)
val LeakWarm       = Color(0x33FFA500)  // subtle orange leak
val LeakCool       = Color(0x2200BFFF)  // subtle blue leak
