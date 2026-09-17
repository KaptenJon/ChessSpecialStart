package com.chesspoints.ai

import com.chesspoints.engine.Color
import com.chesspoints.engine.DrawReason
import com.chesspoints.engine.GamePosition
import com.chesspoints.engine.Move
import com.chesspoints.engine.MoveEngine
import com.chesspoints.engine.MoveRequest
import com.chesspoints.engine.MoveType
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PositionStatus
import com.chesspoints.engine.Square
import kotlin.math.abs

data class AiSearchConfig(
    val depth: Int = DEFAULT_DEPTH,
    val timeBudgetMillis: Long? = null,
) {
    init {
        require(depth >= 1) { "depth must be at least 1" }
        require(timeBudgetMillis == null || timeBudgetMillis > 0) { "timeBudgetMillis must be positive when provided" }
    }

    companion object {
        const val DEFAULT_DEPTH: Int = 2
    }
}

data class AiMoveChoice(
    val move: Move?,
    val score: Int,
    val searchedDepth: Int,
    val evaluatedNodes: Int,
    val completedRequestedDepth: Boolean,
)

class AiMoveEngine {
    fun chooseMove(
        position: GamePosition,
        config: AiSearchConfig = AiSearchConfig(),
    ): AiMoveChoice {
        val rootMoves = orderedMoves(MoveEngine.legalMoves(position))
        if (rootMoves.isEmpty()) {
            val terminalScore = scoreStatus(MoveEngine.evaluate(position), position.sideToMove, 0)
            return AiMoveChoice(
                move = null,
                score = terminalScore,
                searchedDepth = 0,
                evaluatedNodes = 0,
                completedRequestedDepth = true,
            )
        }

        val deadlineNanos = config.timeBudgetMillis?.let { System.nanoTime() + (it * 1_000_000L) }
        val rootColor = position.sideToMove
        var evaluatedNodes = 0
        var bestMove = rootMoves.first()
        var bestScore = Int.MIN_VALUE / 4
        var completedDepth = 0

        for (currentDepth in 1..config.depth) {
            try {
                val result = searchRoot(position, rootColor, currentDepth, deadlineNanos) { evaluatedNodes++ }
                bestMove = result.move
                bestScore = result.score
                completedDepth = currentDepth
            } catch (_: SearchTimeoutException) {
                break
            }
        }

        return AiMoveChoice(
            move = bestMove,
            score = bestScore,
            searchedDepth = completedDepth,
            evaluatedNodes = evaluatedNodes,
            completedRequestedDepth = completedDepth == config.depth,
        )
    }

    private fun searchRoot(
        position: GamePosition,
        rootColor: Color,
        depth: Int,
        deadlineNanos: Long?,
        onNode: () -> Unit,
    ): SearchResult {
        var alpha = Int.MIN_VALUE / 4
        val beta = Int.MAX_VALUE / 4
        var bestMove: Move? = null
        var bestScore = Int.MIN_VALUE / 4

        for (move in orderedMoves(MoveEngine.legalMoves(position))) {
            checkDeadline(deadlineNanos)
            val nextPosition = applyMove(position, move)
            val score = minimax(
                position = nextPosition,
                depth = depth - 1,
                alpha = alpha,
                beta = beta,
                maximizing = false,
                rootColor = rootColor,
                deadlineNanos = deadlineNanos,
                ply = 1,
                onNode = onNode,
            )
            if (bestMove == null || score > bestScore) {
                bestMove = move
                bestScore = score
            }
            alpha = maxOf(alpha, bestScore)
        }

        return SearchResult(bestMove ?: error("Expected at least one legal move"), bestScore)
    }

    private fun minimax(
        position: GamePosition,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximizing: Boolean,
        rootColor: Color,
        deadlineNanos: Long?,
        ply: Int,
        onNode: () -> Unit,
    ): Int {
        checkDeadline(deadlineNanos)
        onNode()

        val status = MoveEngine.evaluate(position)
        if (
            depth == 0 ||
            status is PositionStatus.Checkmate ||
            status is PositionStatus.KingCaptured ||
            status is PositionStatus.Draw
        ) {
            return evaluatePosition(position, status, rootColor, ply)
        }

        val legalMoves = orderedMoves(MoveEngine.legalMoves(position))
        if (legalMoves.isEmpty()) {
            return evaluatePosition(position, status, rootColor, ply)
        }

        var localAlpha = alpha
        var localBeta = beta

        if (maximizing) {
            var bestScore = Int.MIN_VALUE / 4
            for (move in legalMoves) {
                val nextPosition = applyMove(position, move)
                val score = minimax(
                    position = nextPosition,
                    depth = depth - 1,
                    alpha = localAlpha,
                    beta = localBeta,
                    maximizing = false,
                    rootColor = rootColor,
                    deadlineNanos = deadlineNanos,
                    ply = ply + 1,
                    onNode = onNode,
                )
                bestScore = maxOf(bestScore, score)
                localAlpha = maxOf(localAlpha, bestScore)
                if (localBeta <= localAlpha) {
                    break
                }
            }
            return bestScore
        }

        var bestScore = Int.MAX_VALUE / 4
        for (move in legalMoves) {
            val nextPosition = applyMove(position, move)
            val score = minimax(
                position = nextPosition,
                depth = depth - 1,
                alpha = localAlpha,
                beta = localBeta,
                maximizing = true,
                rootColor = rootColor,
                deadlineNanos = deadlineNanos,
                ply = ply + 1,
                onNode = onNode,
            )
            bestScore = minOf(bestScore, score)
            localBeta = minOf(localBeta, bestScore)
            if (localBeta <= localAlpha) {
                break
            }
        }
        return bestScore
    }

    private fun evaluatePosition(
        position: GamePosition,
        status: PositionStatus,
        rootColor: Color,
        ply: Int,
    ): Int =
        when (status) {
            is PositionStatus.Checkmate,
            is PositionStatus.KingCaptured,
            is PositionStatus.Draw,
            -> scoreStatus(status, rootColor, ply)

            PositionStatus.Active,
            is PositionStatus.Check,
            -> evaluateStatic(position, rootColor)
        }

    private fun scoreStatus(
        status: PositionStatus,
        rootColor: Color,
        ply: Int,
    ): Int =
        when (status) {
            is PositionStatus.Checkmate -> if (status.winner == rootColor) MATE_SCORE - ply else -MATE_SCORE + ply
            is PositionStatus.KingCaptured -> if (status.winner == rootColor) MATE_SCORE - ply else -MATE_SCORE + ply
            is PositionStatus.Draw -> {
                when (status.reason) {
                    DrawReason.STALEMATE,
                    DrawReason.INSUFFICIENT_MATERIAL,
                    -> 0
                }
            }

            PositionStatus.Active -> 0
            is PositionStatus.Check -> if (status.checkedColor == rootColor) -CHECK_BONUS else CHECK_BONUS
        }

    private fun evaluateStatic(
        position: GamePosition,
        rootColor: Color,
    ): Int {
        var score = 0
        for ((square, piece) in position.board.pieces()) {
            val signed = if (piece.color == rootColor) 1 else -1
            score += signed * (materialValue(piece.type) + positionalValue(piece.type, piece.color, square))
        }

        val sideToMoveBonus = if (position.sideToMove == rootColor) 8 else -8
        val rootInCheckPenalty = if (MoveEngine.isInCheck(position, rootColor)) -CHECK_BONUS else 0
        val enemyInCheckBonus = if (MoveEngine.isInCheck(position, rootColor.opposite())) CHECK_BONUS else 0
        return score + sideToMoveBonus + rootInCheckPenalty + enemyInCheckBonus
    }

    private fun positionalValue(
        pieceType: PieceType,
        color: Color,
        square: Square,
    ): Int {
        val centerBonus = (6 - ((abs(square.file - 3.5) + abs(square.rank - 3.5)) * 2)).toInt()
        val advance = if (color == Color.WHITE) square.rank else 7 - square.rank
        val backRank = color.backRank

        return when (pieceType) {
            PieceType.PAWN -> (advance * 7) + centerBonus
            PieceType.KNIGHT -> (centerBonus * 5)
            PieceType.BISHOP -> (centerBonus * 4) + (advance * 2)
            PieceType.ROOK -> ((7 - abs(square.rank - backRank)) * 2) - (minOf(square.file, 7 - square.file) * 3)
            PieceType.QUEEN -> (centerBonus * 2) + advance
            PieceType.KING -> ((7 - abs(square.rank - backRank)) * 4) + (3 - minOf(abs(square.file - 1), abs(square.file - 6))) * 4
        }
    }

    private fun orderedMoves(moves: List<Move>): List<Move> =
        moves.sortedByDescending(::moveOrderingScore)

    private fun moveOrderingScore(move: Move): Int {
        val captureScore = move.capturedPiece?.let { materialValue(it.type) - (materialValue(move.piece.type) / 10) } ?: 0
        val promotionScore = move.promotion?.let(::materialValue) ?: 0
        val castleScore =
            when (move.type) {
                MoveType.KING_SIDE_CASTLE,
                MoveType.QUEEN_SIDE_CASTLE,
                -> 40

                else -> 0
            }
        return captureScore + promotionScore + castleScore
    }

    private fun materialValue(pieceType: PieceType): Int =
        when (pieceType) {
            PieceType.PAWN -> 100
            PieceType.KNIGHT -> 320
            PieceType.BISHOP -> 330
            PieceType.ROOK -> 500
            PieceType.QUEEN -> 900
            PieceType.KING -> 0
        }

    private fun applyMove(position: GamePosition, move: Move): GamePosition =
        when (val result = MoveEngine.apply(position, MoveRequest(move.from, move.to, move.promotion))) {
            is com.chesspoints.engine.MoveApplicationResult.Success -> result.position
            is com.chesspoints.engine.MoveApplicationResult.Illegal -> error("AI attempted illegal move ${move.from.algebraic}-${move.to.algebraic}")
        }

    private fun checkDeadline(deadlineNanos: Long?) {
        if (deadlineNanos != null && System.nanoTime() >= deadlineNanos) {
            throw SearchTimeoutException
        }
    }

    private data class SearchResult(
        val move: Move,
        val score: Int,
    )

    private data object SearchTimeoutException : RuntimeException()

    companion object {
        private const val MATE_SCORE = 100_000
        private const val CHECK_BONUS = 25
    }
}
