package com.chesspoints.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chesspoints.engine.Board
import com.chesspoints.engine.Move
import com.chesspoints.engine.Piece
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.Square
import com.chesspoints.ui.shared.ChessBoard
import com.chesspoints.ui.shared.PieceBadge

@Composable
fun GameScreen(
    board: Board,
    currentTurnLabel: String,
    selectedSquare: Square?,
    legalMoves: List<Move>,
    capturedByWhite: List<Piece>,
    capturedByBlack: List<Piece>,
    statusMessage: String,
    promotionChoices: List<Move>,
    interactionEnabled: Boolean,
    onSquareSelected: (Square) -> Unit,
    onPromotionSelected: (PieceType) -> Unit,
    onPromotionDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "$currentTurnLabel to move", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
            }
        }

        CapturedPiecesRow(
            label = "Captured by White",
            pieces = capturedByWhite,
        )

        ChessBoard(
            board = board,
            selectedSquare = selectedSquare,
            highlightedSquares = legalMoves.mapTo(linkedSetOf()) { it.to },
            placementZone = null,
            onSquareTap = onSquareSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        CapturedPiecesRow(
            label = "Captured by Black",
            pieces = capturedByBlack,
        )

        if (promotionChoices.isNotEmpty()) {
            PromotionDialog(
                choices = promotionChoices.mapNotNull { it.promotion }.distinct(),
                onSelected = onPromotionSelected,
                onDismiss = onPromotionDismissed,
            )
        }
    }
}

@Composable
private fun CapturedPiecesRow(
    label: String,
    pieces: List<Piece>,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (pieces.isEmpty()) {
                Text(text = "No captures yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pieces) { piece ->
                        PieceBadge(
                            letter = piece.type.shortName,
                            isWhite = piece.color == com.chesspoints.engine.Color.WHITE,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionDialog(
    choices: List<PieceType>,
    onSelected: (PieceType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Choose promotion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { pieceType ->
                    TextButton(onClick = { onSelected(pieceType) }) {
                        Text(text = pieceType.displayName)
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private val PieceType.displayName: String
    get() = name.lowercase().replaceFirstChar { it.titlecase() }

private val PieceType.shortName: String
    get() = if (this == PieceType.KNIGHT) "N" else name.first().toString()
