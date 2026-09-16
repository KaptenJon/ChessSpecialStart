package com.chesspoints.ui.placement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chesspoints.engine.Board
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PlacementZone
import com.chesspoints.engine.Square
import com.chesspoints.ui.shared.ChessBoard
import com.chesspoints.ui.shared.PieceBadge

@Composable
fun PlacementScreen(
    board: Board,
    sideToPlace: com.chesspoints.engine.Color,
    sideToPlaceLabel: String,
    placementZone: PlacementZone?,
    remainingPieces: Map<PieceType, Int>,
    selectedPieceType: PieceType?,
    interactionEnabled: Boolean,
    helperMessage: String,
    onPieceSelected: (PieceType) -> Unit,
    onSquareSelected: (Square) -> Unit,
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
                Text(text = "$sideToPlaceLabel places next", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(text = helperMessage, style = MaterialTheme.typography.bodyMedium)
                if (placementZone != null) {
                    Text(
                        text = "Allowed ranks: ${placementZone.ranks.first + 1}-${placementZone.ranks.last + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        ChessBoard(
            board = board,
            selectedSquare = null,
            highlightedSquares = emptySet(),
            placementZone = placementZone,
            onSquareTap = onSquareSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(text = "Unplaced pieces", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(PieceType.entries) { pieceType ->
                val count = remainingPieces.getOrDefault(pieceType, 0)
                PlacementPieceCard(
                    pieceType = pieceType,
                    isWhite = sideToPlace == com.chesspoints.engine.Color.WHITE,
                    count = count,
                    selected = selectedPieceType == pieceType,
                    enabled = interactionEnabled && count > 0,
                    onClick = { onPieceSelected(pieceType) },
                )
            }
        }
    }
}

@Composable
private fun PlacementPieceCard(
    pieceType: PieceType,
    isWhite: Boolean,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = if (selected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        colors = colors,
        modifier = Modifier
            .heightIn(min = 96.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PieceBadge(
                letter = pieceType.shortName,
                isWhite = isWhite,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = pieceType.displayName, fontWeight = FontWeight.SemiBold)
                Text(text = "×$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val PieceType.displayName: String
    get() = name.lowercase().replaceFirstChar { it.titlecase() }

private val PieceType.shortName: String
    get() = if (this == PieceType.KNIGHT) "N" else name.first().toString()
