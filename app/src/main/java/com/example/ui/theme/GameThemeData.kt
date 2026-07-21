package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class GameTheme(
    val id: String,
    val name: String,
    val backgroundBrush: Brush,
    val cardBackground: Color,
    val cardBorderColor: Color,
    val cardBackGradient: Brush,
    val accentColor: Color,
    val textColor: Color,
    val particleColors: List<Color>,
    val cost: Int,
    val isDark: Boolean
)

val GameThemes = mapOf(
    "synthwave" to GameTheme(
        id = "synthwave",
        name = "Neon Synthwave",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF130924), Color(0xFF280B3E), Color(0xFF0F051D))
        ),
        cardBackground = Color(0xFF1F1135),
        cardBorderColor = Color(0xFFFF007F),
        cardBackGradient = Brush.linearGradient(
            colors = listOf(Color(0xFFFF007F), Color(0xFF00F0FF))
        ),
        accentColor = Color(0xFF00F0FF),
        textColor = Color(0xFFFFFFFF),
        particleColors = listOf(Color(0xFFFF007F), Color(0xFF00F0FF), Color(0xFFDFFF00)),
        cost = 0,
        isDark = true
    ),
    "cosmic" to GameTheme(
        id = "cosmic",
        name = "Cosmic Space",
        backgroundBrush = Brush.radialGradient(
            colors = listOf(Color(0xFF15183F), Color(0xFF07091B))
        ),
        cardBackground = Color(0xFF1B1E4E),
        cardBorderColor = Color(0xFFBB86FC),
        cardBackGradient = Brush.linearGradient(
            colors = listOf(Color(0xFFBB86FC), Color(0xFF3700B3))
        ),
        accentColor = Color(0xFFBB86FC),
        textColor = Color(0xFFE0E0FF),
        particleColors = listOf(Color(0xFFBB86FC), Color(0xFF03DAC6), Color(0xFFFFFFFF)),
        cost = 200,
        isDark = true
    ),
    "sunset" to GameTheme(
        id = "sunset",
        name = "Sunset Dream",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFF5E62), Color(0xFFFF9966))
        ),
        cardBackground = Color(0xFFFFECE6),
        cardBorderColor = Color(0xFFFF5E62),
        cardBackGradient = Brush.linearGradient(
            colors = listOf(Color(0xFFFFE259), Color(0xFFFF758C))
        ),
        accentColor = Color(0xFFFF5E62),
        textColor = Color(0xFF4A1521),
        particleColors = listOf(Color(0xFFFFE259), Color(0xFFFF5E62), Color(0xFFFFFFFF)),
        cost = 350,
        isDark = false
    ),
    "forest" to GameTheme(
        id = "forest",
        name = "Forest Zen",
        backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0B1E14), Color(0xFF1A3B22), Color(0xFF0B1E14))
        ),
        cardBackground = Color(0xFF1E4429),
        cardBorderColor = Color(0xFF55E57E),
        cardBackGradient = Brush.linearGradient(
            colors = listOf(Color(0xFF00FF87), Color(0xFF60EFFF))
        ),
        accentColor = Color(0xFF55E57E),
        textColor = Color(0xFFE8F5E9),
        particleColors = listOf(Color(0xFF00FF87), Color(0xFFE1F5FE), Color(0xFF76FF03)),
        cost = 500,
        isDark = true
    ),
    "cyberpunk" to GameTheme(
        id = "cyberpunk",
        name = "Cyber Tech",
        backgroundBrush = Brush.horizontalGradient(
            colors = listOf(Color(0xFF111111), Color(0xFF1D1F21))
        ),
        cardBackground = Color(0xFF252525),
        cardBorderColor = Color(0xFFFFFF00),
        cardBackGradient = Brush.linearGradient(
            colors = listOf(Color(0xFFFFFF00), Color(0xFFFF3300))
        ),
        accentColor = Color(0xFFFFFF00),
        textColor = Color(0xFFEEEEEE),
        particleColors = listOf(Color(0xFFFFFF00), Color(0xFFFF3300), Color(0xFF00FF00)),
        cost = 650,
        isDark = true
    )
)
