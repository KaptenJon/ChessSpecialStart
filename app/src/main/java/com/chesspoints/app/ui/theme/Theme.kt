package com.chesspoints.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ChessGreen,
    onPrimary = Color.White,
    primaryContainer = ChessGreenLight,
    onPrimaryContainer = ChessGreenDeep,
    secondary = ChessGold,
    onSecondary = Color.White,
    secondaryContainer = ChessGoldLight,
    onSecondaryContainer = ChessGoldDeep,
    tertiary = ChessGreenDeep,
    surface = ChessSurface,
    onSurface = ChessInk,
    surfaceVariant = ChessSurfaceVariant,
    onSurfaceVariant = ChessInkMuted,
    surfaceContainer = ChessSurfaceElevated,
    surfaceContainerHigh = ChessSurfaceElevated,
    background = ChessSurface,
    onBackground = ChessInk,
    outline = ChessOutlineLight,
    outlineVariant = ChessOutlineLight,
    error = ChessError,
    errorContainer = ChessErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = ChessGold,
    onPrimary = ChessGoldDeep,
    primaryContainer = Color(0xFF274E3C),
    onPrimaryContainer = ChessGreenLight,
    secondary = ChessGreenLight,
    onSecondary = ChessGreenDeep,
    secondaryContainer = Color(0xFF355745),
    onSecondaryContainer = ChessGreenLight,
    tertiary = ChessGoldLight,
    surface = ChessNightSurface,
    onSurface = ChessNightInk,
    surfaceVariant = ChessNightSurfaceVariant,
    onSurfaceVariant = ChessNightInkMuted,
    surfaceContainer = ChessNightSurfaceVariant,
    surfaceContainerHigh = ChessNightSurfaceVariant,
    background = ChessNight,
    onBackground = ChessNightInk,
    outline = ChessOutlineDark,
    outlineVariant = ChessOutlineDark,
    error = ChessErrorDark,
)

@Composable
fun ChessPointsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val boardPalette = if (darkTheme) DarkBoardPalette else LightBoardPalette

    CompositionLocalProvider(LocalBoardPalette provides boardPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ChessPointsTypography,
            shapes = ChessPointsShapes,
            content = content,
        )
    }
}
