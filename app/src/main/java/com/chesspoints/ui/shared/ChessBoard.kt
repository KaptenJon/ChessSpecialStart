package com.chesspoints.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chesspoints.engine.Board
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PlacementZone
import com.chesspoints.engine.Square

@Composable
fun ChessBoard(
    board: Board,
    selectedSquare: Square?,
    highlightedSquares: Set<Square>,
    placementZone: PlacementZone?,
    onSquareTap: (Square) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (rank in 7 downTo 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(8f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (file in 0..7) {
                        val square = Square(file, rank)
                        val piece = board[square]
                        val isLightSquare = (file + rank) % 2 == 0
                        val isHighlighted = square in highlightedSquares
                        val isSelected = selectedSquare == square
                        val isInPlacementZone = placementZone?.contains(square) == true
                        val squareColor = when {
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            isHighlighted -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.40f)
                            isLightSquare -> Color(0xFFF2E8D5)
                            else -> Color(0xFFB48B64)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(squareColor)
                                .then(
                                    if (isInPlacementZone) {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { onSquareTap(square) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (piece != null) {
                                PieceBadge(
                                    letter = piece.type.shortName,
                                    isWhite = piece.color == com.chesspoints.engine.Color.WHITE,
                                )
                            } else if (isHighlighted) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f))
                                        .padding(10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PieceBadge(
    letter: String,
    isWhite: Boolean,
) {
    Surface(
        shape = CircleShape,
        color = if (isWhite) Color(0xFFF9F5EE) else Color(0xFF2C241D),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Text(
            text = letter,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (isWhite) Color(0xFF231D17) else Color(0xFFF8F4EC),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val PieceType.shortName: String
    get() = if (this == PieceType.KNIGHT) "N" else name.first().toString()
