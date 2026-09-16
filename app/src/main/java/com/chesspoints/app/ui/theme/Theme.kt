package com.chesspoints.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ChessGreen,
    secondary = ChessGold,
    surface = ChessSurface
)

private val DarkColors = darkColorScheme(
    primary = ChessGold,
    secondary = ChessGreen,
    surface = ChessNight
)

@Composable
fun ChessPointsTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChessPointsTypography,
        content = content
    )
}
