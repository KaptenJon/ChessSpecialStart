package com.chesspoints.ai

import com.chesspoints.engine.Color
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PlacementResult
import com.chesspoints.engine.PlacementState
import com.chesspoints.engine.PlacementValidator
import com.chesspoints.engine.Square
import kotlin.math.abs

data class AiPlacementChoice(
    val pieceType: PieceType,
    val square: Square,
)

class AiPlacer {
    fun nextPlacement(
        state: PlacementState,
        color: Color = state.sideToPlace,
    ): AiPlacementChoice? {
        if (color != state.sideToPlace) {
            return null
        }

        val validator = PlacementValidator(state.rules)
        val candidates = buildList {
            for ((pieceType, count) in state.remainingPieces(color)) {
                if (count <= 0) continue
                for (square in placementSquaresFor(color, state.rules.zoneFor(color).ranks)) {
                    val result = validator.place(state, color, pieceType, square)
                    if (result is PlacementResult.Success) {
                        add(AiPlacementChoice(pieceType, square) to scorePlacement(state, color, pieceType, square))
                    }
                }
            }
        }

        return candidates.maxByOrNull { it.second }?.first
    }

    private fun placementSquaresFor(color: Color, ranks: IntRange): List<Square> {
        val files = (0..7).sortedBy { abs(it - 3) }
        val orderedRanks =
            if (color == Color.WHITE) {
                ranks.toList().sortedByDescending { it }
            } else {
                ranks.toList().sorted()
            }
        return buildList {
            for (rank in orderedRanks) {
                for (file in files) {
                    add(Square(file, rank))
                }
            }
        }
    }

    private fun scorePlacement(
        state: PlacementState,
        color: Color,
        pieceType: PieceType,
        square: Square,
    ): Double {
        val zone = state.rules.zoneFor(color).ranks
        val backRank = if (color == Color.WHITE) zone.first else zone.last
        val frontRank = if (color == Color.WHITE) zone.last else zone.first
        val centerDistance = kotlin.math.abs(square.file - 3.5)
        val edgeDistance = minOf(square.file, 7 - square.file).toDouble()
        val cornerishDistance = minOf(abs(square.file - 1), abs(square.file - 6)).toDouble()
        val friendlyNeighbors = adjacentSquares(square).count { neighbor ->
            state.board[neighbor]?.color == color
        }.toDouble()
        val nearestEnemyDistance = state.board.pieces(color.opposite())
            .minOfOrNull { (enemySquare, _) -> manhattan(square, enemySquare).toDouble() }
            ?: 6.0

        return when (pieceType) {
            PieceType.KING -> 100.0 -
                (abs(square.rank - backRank) * 18.0) -
                (cornerishDistance * 10.0) +
                (friendlyNeighbors * 3.0) +
                nearestEnemyDistance

            PieceType.QUEEN -> 72.0 -
                (centerDistance * 8.0) -
                (abs(square.rank - backRank) * 4.0)

            PieceType.ROOK -> 66.0 -
                (abs(square.rank - backRank) * 6.0) -
                (edgeDistance * 5.0)

            PieceType.BISHOP -> 64.0 -
                (centerDistance * 7.0) -
                (abs(square.rank - frontRank) * 6.0)

            PieceType.KNIGHT -> 68.0 -
                (centerDistance * 9.0) -
                (abs(square.rank - frontRank) * 7.0)

            PieceType.PAWN -> 56.0 -
                (abs(square.rank - frontRank) * 14.0) -
                (centerDistance * 3.0)
        }
    }

    private fun adjacentSquares(square: Square): List<Square> =
        buildList {
            for (fileDelta in -1..1) {
                for (rankDelta in -1..1) {
                    if (fileDelta == 0 && rankDelta == 0) continue
                    square.offset(fileDelta, rankDelta)?.let(::add)
                }
            }
        }

    private fun manhattan(a: Square, b: Square): Int = abs(a.file - b.file) + abs(a.rank - b.rank)
}
