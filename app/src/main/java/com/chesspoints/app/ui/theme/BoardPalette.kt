package com.chesspoints.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Board-specific colours that are not part of the Material colour scheme.
 * Kept in the theme so light and dark boards stay in step with the rest of
 * the app surfaces.
 */
@Immutable
data class BoardPalette(
    val frameTop: Color,
    val frameBottom: Color,
    val frameEdge: Color,
    val lightSquare: Color,
    val darkSquare: Color,
    val lightSquareLabel: Color,
    val darkSquareLabel: Color,
    val selection: Color,
    val moveTarget: Color,
    val captureTarget: Color,
    val zoneTint: Color,
    val zoneBorder: Color,
)

val LightBoardPalette = BoardPalette(
    frameTop = Color(0xFF4B372A),
    frameBottom = Color(0xFF2E2019),
    frameEdge = Color(0xFF7A5A40),
    lightSquare = Color(0xFFF0DFC0),
    darkSquare = Color(0xFF7D5741),
    lightSquareLabel = Color(0xFF7D5741),
    darkSquareLabel = Color(0xFFF0DFC0),
    selection = Color(0xFFE8B84B),
    moveTarget = Color(0xFF1F6048),
    captureTarget = Color(0xFFC0442F),
    zoneTint = Color(0x261F6048),
    zoneBorder = Color(0xFF2E8A66),
)

val DarkBoardPalette = BoardPalette(
    frameTop = Color(0xFF33261E),
    frameBottom = Color(0xFF1A1310),
    frameEdge = Color(0xFF55402F),
    lightSquare = Color(0xFFCBB48F),
    darkSquare = Color(0xFF5E4131),
    lightSquareLabel = Color(0xFF5E4131),
    darkSquareLabel = Color(0xFFE2D2B4),
    selection = Color(0xFFE8B84B),
    moveTarget = Color(0xFF7FD3AC),
    captureTarget = Color(0xFFE2705A),
    zoneTint = Color(0x337FD3AC),
    zoneBorder = Color(0xFF7FD3AC),
)

val LocalBoardPalette = staticCompositionLocalOf { LightBoardPalette }
